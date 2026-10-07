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
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import net.sf.freecol.common.model.HistoryEvent;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.MarketData;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Region;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * The original's <b>woodcuts</b> (master plan W9, N17): a full-screen
 * picture in a wooden frame with its title on a ribbon, shown once per game
 * at an event (the discovery of the New World, the first colony, the first
 * meeting with the natives ...).  Static, headless painters and rules, in
 * the style of {@link ClassicDeparture}; {@link ClassicAdvisorLayer} shows
 * them (one queue with the advisor boxes) and {@link ClassicGUI} has the
 * triggers.
 *
 * <p><b>The picture (V, 0 px in 12 clip frames).</b>  On a black screen
 * (menu strip, map and panel all black): {@code WOODFRAM.SS.000} at
 * {@link #FRAME_X},{@link #FRAME_Y}; its opening filled with
 * {@link #FILL} (index 10) over {@link #OPENING}; the ribbon at
 * {@link #RIBBON_Y}: {@code NAMEPLAT.SS.000}, n times
 * {@code NAMEPLAT.SS.001}, {@code NAMEPLAT.SS.002}, centred, with
 * n = ceil(advance / 16) ({@link #ribbonPieces}); the title, WOODCUT.TXT
 * entry k ({@link ClassicText#woodcuts}), in FONT-NP at {@link #TITLE_Y},
 * left x = (320 - advance + 1) / 2, in {@link #TITLE_1}, {@link #TITLE_2},
 * {@link #TITLE_3}; and last {@code WDCUT<k>.SS.000} at the opening's
 * corner (a 192x115 picture covers the frame's rows 153-154; its
 * transparent pixels show the fill).  Landfall {@code 02_}, {@code 07_},
 * {@code 13_} and clip008 #3381/#3442, #52688/#52745 and fog-start
 * #1053/#1110: 0 px off ({@code ClassicWoodcutTest}).
 *
 * <p><b>The timeline (V, six recordings).</b>  The trigger's paint, then
 * the black after {@link #BLACK_AFTER_TRIGGER_MS}; the frame, ribbon, title
 * and fill in one paint {@link #FRAME_AFTER_BLACK_MS} later; the picture
 * dissolves in {@link #DISSOLVE_AFTER_FRAME_MS} later, evenly over
 * {@link #DISSOLVE_MS} (55 frames from the first to the last changed one);
 * it stays until a key; then black, the game palette back one frame later,
 * the map {@link #MAP_BACK_MS} after the black, redrawn from the current
 * state.  The water cycle is frozen from the black to the palette's return.
 * The dissolve uses the departure's fixed seeded order
 * ({@link ClassicDeparture#dissolveOrder}): the original's order is fixed
 * too (the same in all six recordings) but unknown, so mid-dissolve frames
 * differ from it, settled ones do not.
 *
 * <p><b>The palette.</b>  The original loads the woodcut's own palette at
 * the black: the union of the index sheets of WOODFRAM, NAMEPLAT and
 * WDCUT{@code <k>}, plus 94 for the title ({@link #palette}); the
 * recorder's frames take it ({@link ClassicFrameRecorder#woodcutPalette}),
 * and the mouse arrow's grey (index 7) is {@link #ARROW_DIM} meanwhile.
 *
 * <p>All rules are pure and the art comes from the pack only; without it
 * no woodcut is shown (one INFO line), and none is marked as shown.
 */
final class ClassicWoodcut {

    private static final Logger logger = Logger.getLogger(ClassicWoodcut.class.getName());

    /** The canvas. */
    static final int W = 320, H = 200;

    /** The WOODCUT.TXT entries: 0 (never shown, no picture) to 13. */
    static final int ENTRIES = 14;

    /** The entries that have a picture. */
    static final int FIRST = 1, LAST = 13;

    /**
     * The entries (k = the WOODCUT.TXT line and the WDCUT number; the
     * landfall analysis numbers its woodcuts by appearance instead).
     */
    static final int DISCOVERY = 1, COLONY = 2, NATIVES = 3, AZTECS = 4,
        INCAS = 5, PACIFIC = 6, VILLAGE = 7, FOUNTAIN = 8, CARGO = 9,
        EUROPEANS = 10, BURNING = 11, DESTROYED = 12, RAID = 13;

    /** The frame's sprite and its place. */
    static final String FRAME_SPRITE = "WOODFRAM.SS.000";
    static final int FRAME_X = 23, FRAME_Y = 15;

    /** The ribbon's sheet: left end, middle piece, right end. */
    static final String RIBBON_SHEET = "NAMEPLAT.SS";

    /** The ribbon's row and its pieces' widths. */
    static final int RIBBON_Y = 162, RIBBON_END_W = 18, RIBBON_PIECE_W = 16;

    /** The opening's fill (index 10), under the picture. */
    static final Rectangle OPENING = new Rectangle(63, 40, 192, 112);
    static final int FILL = 0x5D3824;

    /** Where the picture goes, and the area its dissolve repaints. */
    static final int PICTURE_X = 63, PICTURE_Y = 40;
    static final Rectangle PICTURE = new Rectangle(PICTURE_X, PICTURE_Y, 192, 115);

    /** The title's glyph cell top and its colours (indices 92, 94, 93). */
    static final int TITLE_Y = 165;
    static final int TITLE_1 = 0x755924, TITLE_2 = 0x61411C, TITLE_3 = 0x2C200C;

    /** The palette entry of {@link #TITLE_2}, in no sheet of the woodcut. */
    static final int TITLE_2_INDEX = 94;

    /** The mouse arrow's grey, and its colour under the woodcut's palette (index 7). */
    static final int ARROW_GREY = 0xAAAAAA, ARROW_DIM = 0x797979;


    // The timeline (V, landfall, fog-start, clip008; spec G5 section 2.2)

    /** One frame of the original (70.0863 Hz), ms. */
    static final double FRAME_MS = 1000.0 / 70.0863;

    /** From the trigger's paint to the black (4 frames; 3-6 seen). */
    static final double BLACK_AFTER_TRIGGER_MS = 57.0;

    /** From the new colony on the map to the black (5 frames, clip008 #3369). */
    static final double BLACK_AFTER_COLONY_MS = 72.0;

    /** From the black to the frame, ribbon, title and fill (6 frames). */
    static final double FRAME_AFTER_BLACK_MS = 86.0;

    /** From the frame to the dissolve's first frame (3 frames; 3-8 seen). */
    static final double DISSOLVE_AFTER_FRAME_MS = 43.0;

    /** From the dissolve's first changed frame to its last (54 frame steps). */
    static final double DISSOLVE_MS = 770.0;

    /** The dissolve's frames, the first and the last included. */
    static final int DISSOLVE_FRAMES = 55;

    /** From the key's black to the map (21 frames; 21-31 seen). */
    static final double MAP_BACK_MS = 300.0;

    /**
     * From the map's return to what follows (V): the New World's name
     * (W10), the chief's box, the village box, the colony screen, Europe.
     */
    static final double FOLLOW_DISCOVERY_MS = 71.0, FOLLOW_NATIVES_MS = 171.0,
        FOLLOW_VILLAGE_MS = 128.0, FOLLOW_COLONY_MS = 328.0, FOLLOW_CARGO_MS = 57.0;


    /** Whether the "woodcuts unavailable" INFO line was logged. */
    private static boolean missLogged = false;


    private ClassicWoodcut() {}


    // Geometry

    /**
     * The ribbon's middle pieces for a title: the five recorded titles fit
     * n = ceil(advance / 16) at 0 px (no margin).
     *
     * @param advance The title's FONT-NP advance.
     * @return n, at least 0.
     */
    static int ribbonPieces(int advance) {
        return Math.max(0, (advance + RIBBON_PIECE_W - 1) / RIBBON_PIECE_W);
    }

    /**
     * @param pieces The ribbon's middle pieces.
     * @return The ribbon's left edge, centred.
     */
    static int ribbonX(int pieces) {
        return (W - (2 * RIBBON_END_W + RIBBON_PIECE_W * pieces)) / 2;
    }

    /**
     * The title's left edge: the box centring's rounding (W0c); the odd
     * advances 225 and 181 decide it.
     *
     * @param advance The title's FONT-NP advance.
     * @return The left edge.
     */
    static int titleX(int advance) {
        return (W - advance + 1) / 2;
    }

    /**
     * @param k An entry.
     * @return {@code WDCUT<k>.SS}, e.g. {@code WDCUT03.SS}.
     */
    static String pictureSheet(int k) {
        return String.format(java.util.Locale.ROOT, "WDCUT%02d.SS", k);
    }

    /**
     * @param k An entry.
     * @return Its bit in a set of woodcuts.
     */
    static int bit(int k) {
        return (k < 0 || k >= 31) ? 0 : 1 << k;
    }


    // The art

    /** The pack's pieces of the woodcuts. */
    static final class Art {

        final BufferedImage frame;
        final BufferedImage[] ribbon;
        final BufferedImage[] pictures;
        final ClassicFont font;
        final List<String> titles;

        Art(BufferedImage frame, BufferedImage[] ribbon, BufferedImage[] pictures,
            ClassicFont font, List<String> titles) {
            this.frame = frame;
            this.ribbon = ribbon;
            this.pictures = pictures;
            this.font = font;
            this.titles = titles;
        }

        /**
         * @param k An entry.
         * @return Whether woodcut k can be drawn.
         */
        boolean has(int k) {
            return k >= FIRST && k <= LAST && k < this.pictures.length
                && this.pictures[k] != null && k < this.titles.size();
        }

        /**
         * @param k An entry.
         * @return Its title (decoded WOODCUT.TXT), or "".
         */
        String title(int k) {
            return (k >= 0 && k < this.titles.size()) ? this.titles.get(k) : "";
        }
    }

    /**
     * The woodcuts' art of a pack.
     *
     * @param pack The pack (may be null).
     * @return The art, or null (logged once) when the pack lacks the frame,
     *     the ribbon, FONT-NP or WOODCUT.TXT; a missing picture only drops
     *     its woodcut.
     */
    static Art load(ClassicPackFiles pack) {
        if (pack == null) return miss("no classic_original pack");
        final BufferedImage frame = pack.image(ClassicPackFiles.ssKey(FRAME_SPRITE));
        final BufferedImage[] ribbon = new BufferedImage[3];
        for (int i = 0; i < 3; i++) {
            ribbon[i] = pack.image(ClassicPackFiles.ssKey(RIBBON_SHEET + ".00" + i));
        }
        final ClassicFont font = pack.font(ClassicFont.NP);
        final List<String> titles = ClassicText.woodcuts(pack);
        if (frame == null || ribbon[0] == null || ribbon[1] == null
            || ribbon[2] == null || font == null || titles == null) {
            return miss("the classic_original pack lacks WOODFRAM, NAMEPLAT,"
                + " FONT-NP or WOODCUT.TXT");
        }
        final BufferedImage[] pictures = new BufferedImage[ENTRIES];
        for (int k = FIRST; k <= LAST; k++) {
            pictures[k] = pack.image(ClassicPackFiles.ssKey(pictureSheet(k) + ".000"));
        }
        return new Art(frame, ribbon, pictures, font, titles);
    }

    private static synchronized Art miss(String why) {
        if (!missLogged) {
            missLogged = true;
            logger.info(why + " -- no woodcuts (re-run ant classic-assets)");
        }
        return null;
    }


    // Painting

    /**
     * The screen before the dissolve: black, the frame, the opening's
     * fill, the ribbon and the title.
     *
     * @param a The art.
     * @param k The entry.
     * @return A 320x200 RGB image.
     */
    static BufferedImage frameOnly(Art a, int k) {
        final BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, W, H);
            g.drawImage(a.frame, FRAME_X, FRAME_Y, null);
            g.setColor(new Color(FILL));
            g.fill(OPENING);
            final String title = a.title(k);
            final int advance = a.font.stringWidth(title);
            final int n = ribbonPieces(advance);
            int x = ribbonX(n);
            g.drawImage(a.ribbon[0], x, RIBBON_Y, null);
            x += RIBBON_END_W;
            for (int i = 0; i < n; i++, x += RIBBON_PIECE_W) {
                g.drawImage(a.ribbon[1], x, RIBBON_Y, null);
            }
            g.drawImage(a.ribbon[2], x, RIBBON_Y, null);
            a.font.draw(g, title, titleX(advance), TITLE_Y,
                        ClassicFont.colours(TITLE_1, TITLE_2, TITLE_3));
        } finally {
            g.dispose();
        }
        return img;
    }

    /**
     * The finished screen: {@link #frameOnly} with the picture, drawn last.
     *
     * @param a The art.
     * @param k The entry.
     * @return A 320x200 RGB image.
     */
    static BufferedImage finished(Art a, int k) {
        final BufferedImage img = frameOnly(a, k);
        final BufferedImage pic = (k >= 0 && k < a.pictures.length) ? a.pictures[k] : null;
        if (pic != null) {
            final Graphics2D g = img.createGraphics();
            try {
                g.drawImage(pic, PICTURE_X, PICTURE_Y, null);
            } finally {
                g.dispose();
            }
        }
        return img;
    }

    /**
     * How many of a dissolve's pixels are shown {@code sinceMs} after its
     * first frame: 1/55 in the first, all in the 55th (at
     * {@link #DISSOLVE_MS}), evenly in between; a late tick catches up.
     *
     * @param changed The pixels the dissolve reveals.
     * @param sinceMs Milliseconds since the dissolve's first frame
     *     (negative: none yet).
     * @return 0..changed.
     */
    static int revealed(int changed, double sinceMs) {
        if (changed <= 0 || sinceMs < 0.0) return 0;
        final double step = DISSOLVE_MS / (DISSOLVE_FRAMES - 1);
        final double f = (sinceMs + step) / (DISSOLVE_MS + step);
        if (f >= 1.0) return changed;
        return (int) Math.floor(changed * f);
    }


    /** One woodcut on the screen: what it shows now (EDT only). */
    static final class Screen {

        /** The entry. */
        final int k;

        /** What is shown, 320x200 RGB, and its pixels (shared). */
        final BufferedImage image;
        private final int[] shown;

        /** The frame-only and the finished screen's pixels. */
        private final int[] frameOnly, finished;

        /** The dissolve's order ({@link ClassicDeparture#dissolveOrder}). */
        private final int[] order;

        /** The dissolve's pixels shown so far. */
        private int revealed = 0;

        /**
         * @param k The entry.
         * @param frameOnly {@link ClassicWoodcut#frameOnly}.
         * @param finished {@link ClassicWoodcut#finished}.
         */
        Screen(int k, BufferedImage frameOnly, BufferedImage finished) {
            this.k = k;
            this.image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            this.shown = ClassicDeparture.pixels(this.image);
            this.frameOnly = frameOnly.getRGB(0, 0, W, H, null, 0, W);
            this.finished = finished.getRGB(0, 0, W, H, null, 0, W);
            for (int i = 0; i < this.frameOnly.length; i++) {
                this.frameOnly[i] &= 0xFFFFFF;
                this.finished[i] &= 0xFFFFFF;
            }
            this.order = ClassicDeparture.dissolveOrder(this.frameOnly, this.finished);
        }

        /**
         * @param a The art.
         * @param k An entry.
         * @return Its screen, or null when the art lacks it.
         */
        static Screen of(Art a, int k) {
            if (a == null || !a.has(k)) return null;
            return new Screen(k, frameOnly(a, k), finished(a, k));
        }

        /** Black, the whole screen. */
        void black() {
            Arrays.fill(this.shown, 0);
        }

        /** The frame, ribbon, title and fill: the dissolve starts here. */
        void frame() {
            System.arraycopy(this.frameOnly, 0, this.shown, 0, this.shown.length);
            this.revealed = 0;
        }

        /** @return The dissolve's pixels, all of them. */
        int changed() {
            return this.order.length;
        }

        /** @return The dissolve's pixels shown so far. */
        int revealed() {
            return this.revealed;
        }

        /**
         * Show the dissolve's first {@code n} pixels.
         *
         * @param n The pixels.
         * @return Whether any pixel changed.
         */
        boolean dissolveTo(int n) {
            final int to = Math.min(Math.max(n, 0), this.order.length);
            if (to <= this.revealed) return false;
            ClassicDeparture.apply(this.shown, this.finished, this.order,
                                   this.revealed, to);
            this.revealed = to;
            return true;
        }

        /** @return Whether the picture is complete. */
        boolean complete() {
            return this.revealed >= this.order.length;
        }

        /** @return A copy of the shown pixels (tests). */
        int[] pixels() {
            return this.shown.clone();
        }
    }


    // The palette

    /**
     * The woodcut's palette (class comment): every index the frame, the
     * ribbon and the picture use with its colour, plus 94 for the title.
     *
     * @param pack The pack, or null.
     * @param a The art.
     * @param k The entry.
     * @return 256 entries 0xRRGGBB, -1 where the woodcut has none; null
     *     without the index sheets.
     */
    static int[] palette(ClassicPackFiles pack, Art a, int k) {
        if (pack == null || a == null || !a.has(k)) return null;
        final int[] out = new int[256];
        Arrays.fill(out, -1);
        boolean ok = add(out, pack.indexSheet("WOODFRAM.SS"), 0, a.frame);
        final ClassicIndexSheet ribbon = pack.indexSheet(RIBBON_SHEET);
        for (int f = 0; f < 3; f++) ok &= add(out, ribbon, f, a.ribbon[f]);
        ok &= add(out, pack.indexSheet(pictureSheet(k)), 0, a.pictures[k]);
        if (!ok) return null;
        out[TITLE_2_INDEX] = TITLE_2;
        return out;
    }

    /** Add a sprite's index to colour pairs; false without its sheet. */
    private static boolean add(int[] out, ClassicIndexSheet sheet, int frame,
                               BufferedImage sprite) {
        if (sheet == null || sprite == null || frame >= sheet.size()
            || sheet.width(frame) != sprite.getWidth()
            || sheet.height(frame) != sprite.getHeight()) return false;
        for (int y = 0; y < sprite.getHeight(); y++) {
            for (int x = 0; x < sprite.getWidth(); x++) {
                final int i = sheet.index(frame, x, y);
                if (i == ClassicIndexSheet.TRANSPARENT) continue;
                final int argb = sprite.getRGB(x, y);
                if ((argb >>> 24) == 0) continue;
                out[i] = argb & 0xFFFFFF;
            }
        }
        return true;
    }

    /**
     * The mouse arrow as it looks under the woodcut's palette: its grey
     * {@link #ARROW_GREY} (index 7) is {@link #ARROW_DIM} there (landfall
     * #2112-#2637, clip008 #3375-#3978: 25 px).
     *
     * @param arrow The arrow (ARGB), or null.
     * @return A recoloured copy, or null.
     */
    static BufferedImage dimArrow(BufferedImage arrow) {
        if (arrow == null) return null;
        final BufferedImage out = new BufferedImage(arrow.getWidth(),
            arrow.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < arrow.getHeight(); y++) {
            for (int x = 0; x < arrow.getWidth(); x++) {
                final int argb = arrow.getRGB(x, y);
                out.setRGB(x, y, ((argb & 0xFFFFFF) == ARROW_GREY && (argb >>> 24) != 0)
                    ? (argb & 0xFF000000) | ARROW_DIM : argb);
            }
        }
        return out;
    }


    // The triggers (spec G5 section 3)

    /**
     * The woodcut of a first meeting with a native nation: the Aztecs and
     * the Incas have their own (I: no clip; FreeCol's meeting picture does
     * the same), any other nation the natives' one.
     *
     * @param suffix The nation's suffix ({@code Nation.getSuffix}).
     * @return {@link #AZTECS}, {@link #INCAS} or {@link #NATIVES}.
     */
    static int contactWoodcut(String suffix) {
        if ("aztec".equals(suffix)) return AZTECS;
        if ("inca".equals(suffix)) return INCAS;
        return NATIVES;
    }

    /**
     * The woodcut a notice brings before its box (N17; I: no clip but
     * k = 9).
     *
     * @param messageId The message's id.
     * @param ladenShip Whether its display object is a ship with goods
     *     aboard (the arrival in Europe).
     * @return The entry, or -1.
     */
    static int messageWoodcut(String messageId, boolean ladenShip) {
        if (messageId == null) return -1;
        switch (messageId) {
        case "model.lostCityRumour.fountainOfYouth.description": return FOUNTAIN;
        case "model.unit.arriveInEurope": return (ladenShip) ? CARGO : -1;
        case "combat.raid.building": return BURNING;
        case "combat.colonyBurned.ours": return DESTROYED;
        case "combat.raid.ours": return RAID;
        default: return -1;
        }
    }

    /**
     * Whether a move enters a native village (woodcut 7 before it, V:
     * landfall #15424): to learn, to scout, as a missionary, to trade, or
     * an attack on it.  A refused move shows nothing.
     *
     * @param move The move type.
     * @param target The tile moved to, or null.
     * @return True for a village entry.
     */
    static boolean entersVillage(Unit.MoveType move, Tile target) {
        if (move == null || target == null
            || !(target.getSettlement() instanceof IndianSettlement)) return false;
        switch (move) {
        case ENTER_INDIAN_SETTLEMENT_WITH_FREE_COLONIST:
        case ENTER_INDIAN_SETTLEMENT_WITH_SCOUT:
        case ENTER_INDIAN_SETTLEMENT_WITH_MISSIONARY:
        case ENTER_SETTLEMENT_WITH_CARRIER_AND_GOODS:
        case ATTACK_SETTLEMENT:
            return true;
        default:
            return false;
        }
    }

    /** FreeCol's sound at a first meeting ({@code setStanceHandler}). */
    static final String MEET_SOUND = "sound.event.meet.";

    /**
     * The nation a meeting sound names.
     *
     * @param sound A sound key.
     * @return The nation id of {@code sound.event.meet.<id>}, or null.
     */
    static String meetNation(String sound) {
        return (sound == null || !sound.startsWith(MEET_SOUND)
                || sound.length() == MEET_SOUND.length()) ? null
            : sound.substring(MEET_SOUND.length());
    }

    /**
     * The woodcuts a game counts as shown although it has no record of
     * them (a save of an older build or of the standard GUI): those whose
     * event has left a trace (spec G5 section 4.2).  Evaluated once, when
     * the game view is built.
     *
     * @param me Our player (null: none).
     * @param map The client's map (null: none).
     * @return The bits.
     */
    static int derived(Player me, Map map) {
        if (me == null) return 0;
        int bits = 0;
        if (map != null) {
            if (!map.getTileSet(t -> t.isExplored() && t.isLand()).isEmpty()) {
                bits |= bit(DISCOVERY);
            }
            for (Region r : map.getRegions()) {
                if (r.isPacific() && r.getDiscoveredIn() != null) bits |= bit(PACIFIC);
            }
        }
        boolean colony = me.getSettlementCount() > 0, destroyed = false;
        for (HistoryEvent h : me.getHistory()) {
            if (h.getEventType() == HistoryEvent.HistoryEventType.FOUND_COLONY) colony = true;
            if (h.getEventType() == HistoryEvent.HistoryEventType.COLONY_DESTROYED) destroyed = true;
        }
        if (colony) bits |= bit(COLONY);
        if (destroyed) bits |= bit(DESTROYED);
        if (me.hasContactedIndians()) bits |= bit(NATIVES);
        if (me.hasContactedEuropeans()) bits |= bit(EUROPEANS);
        if (me.getGame() != null) {
            for (Player p : me.getGame().getLivePlayerList()) {
                if (p == me || p.getNation() == null) continue;
                final int k = contactWoodcut(p.getNation().getSuffix());
                if (k != NATIVES && me.getStance(p) != Stance.UNCONTACTED) bits |= bit(k);
                if (p.isIndian()) {
                    for (IndianSettlement is : p.getIndianSettlementList()) {
                        if (is.hasVisited(me)) {
                            bits |= bit(VILLAGE);
                            break;
                        }
                    }
                }
            }
        }
        if (me.getMarket() != null) {
            for (MarketData md : me.getMarket().getMarketDataValues()) {
                if (md.getSales() > 0) {
                    bits |= bit(CARGO);
                    break;
                }
            }
        }
        return bits;
    }
}
