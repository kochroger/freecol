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
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Modifier;
import net.sf.freecol.common.model.ResourceType;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.UnitType;
import static net.sf.freecol.common.util.CollectionUtils.toList;


/**
 * The original's Colonopedia text (PEDIA.TXT, copied into the pack's
 * {@code text/} folder by {@code ant classic-assets}), its founding
 * father page (build spec D8b, the page alone: F1 in the father box) and
 * its terrain page (part W, build spec M4.11: F1 on the colony screen,
 * {@link #terrainPage}).
 *
 * <p><b>The file.</b>  The same byte codes as GAME.TXT
 * ({@link ClassicText#decode}).  A section starts with a line
 * {@code @NAME} ({@code @FATHER2}); {@code @width=n} and {@code @SMALLFONT}
 * are its directives; a line {@code @;} is a comment and ends the section
 * (the file puts one before each entry).  So the sections cannot go through
 * {@link ClassicText}, whose sections would end at {@code @SMALLFONT}.
 *
 * <p><b>The father page</b> (clip008 01-fathers section 3.1; V, 0 px on
 * #40109 Minuit, #48645 Stuyvesant, #49256 Hudson, #49900 Drake, the
 * pointer apart):
 * <ul>
 *   <li>{@code WOODPANL.PIK} over the whole screen, no picture;</li>
 *   <li>LABELS {@code @MISC} 107 "COLONIZATION-ENZYKLOPÄDIE" and
 *       "(<i>name</i>: Gründerväter)" (NAMES {@code @FATHERS},
 *       PEDIA {@code @PEDIA} 5) in gold, glyph tops 5 and 13, each at
 *       {@code x = (322 - w) div 2};</li>
 *   <li>the entry at {@code x0 = (321 - @width) div 2}: its first line (the
 *       title, without its {@code ^}) at y 36, then its text reflowed
 *       greedily at {@code 320 - 2 x0}, 7 px apart; {@code ^} ends a
 *       paragraph and leaves one blank line (a run of them counts once);
 *       {@code {..}} is gold and carries over line ends; {@code %%} is a
 *       {@code %}, {@code $} FONTTINY's coin; {@code @SMALLFONT} draws the
 *       same font, only its {@code @width} counts.</li>
 * </ul>
 */
final class ClassicPedia {

    private static final Logger logger = Logger.getLogger(ClassicPedia.class.getName());

    /** The file, as the converter names it in {@code <pack>/text}. */
    static final String FILE = "PEDIA.TXT";

    /** The pack key of the page's background. */
    static final String WOODPANL_KEY = "image.classic_original.pik.WOODPANL.PIK";

    /** LABELS.TXT {@code @MISC}: "COLONIZATION-ENZYKLOPÄDIE". */
    static final int MISC_HEADER = 107;

    /** PEDIA.TXT {@code @PEDIA}: the founding fathers' category. */
    static final int CATEGORY_FATHERS = 5;

    /** The glyph tops of the two header lines and of the title. */
    static final int HEADER1_TOP = 5, HEADER2_TOP = 13, TITLE_TOP = 36;

    /** The body's line pitch. */
    static final int PITCH = 7;

    /** The width of an entry without {@code @width}. */
    static final int DEFAULT_WIDTH = 300;

    /** The ink (index 68) and the gold (index 149). */
    static final int INK = 0x559634, GOLD = 0xC7A220;

    /** Loaded texts by pack directory. */
    private static final Map<File, ClassicPedia> CACHE = new HashMap<>();


    /** One section: its directives and its raw lines (markup kept). */
    static final class Entry {

        /** {@code @width}, or {@link #DEFAULT_WIDTH}. */
        final int width;

        /** {@code @SMALLFONT}. */
        final boolean smallFont;

        /** The lines, trailing blank ones dropped. */
        final List<String> lines;

        Entry(int width, boolean smallFont, List<String> lines) {
            this.width = width;
            this.smallFont = smallFont;
            this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        }
    }

    /** The sections by name (without '@'). */
    private final Map<String, Entry> sections;


    private ClassicPedia(Map<String, Entry> sections) {
        this.sections = sections;
    }

    /**
     * The Colonopedia of a pack, read once per pack directory.
     *
     * @param pack The pack, or null.
     * @return The texts, or null without the pack's PEDIA.TXT (re-run
     *     {@code ant classic-assets}).
     */
    static ClassicPedia load(ClassicPackFiles pack) {
        if (pack == null) return null;
        synchronized (CACHE) {
            ClassicPedia p = CACHE.get(pack.directory());
            if (p != null) return p;
            final File f = pack.textFile(FILE);
            if (f == null) {
                logger.info("classic_original pack has no text/" + FILE
                    + " -- no Colonopedia pages (re-run ant classic-assets)");
                return null;
            }
            try {
                p = parse(ClassicText.decode(Files.readAllBytes(f.toPath())));
            } catch (IOException e) {
                logger.log(Level.WARNING, "Unreadable " + f, e);
                return null;
            }
            CACHE.put(pack.directory(), p);
            return p;
        }
    }

    /**
     * Read decoded lines (class comment).
     *
     * @param lines The file's lines ({@link ClassicText#decode}).
     * @return The texts.
     */
    static ClassicPedia parse(List<String> lines) {
        final Map<String, Entry> out = new HashMap<>();
        String name = null;
        int width = DEFAULT_WIDTH;
        boolean small = false;
        final List<String> body = new ArrayList<>();
        for (String raw : lines) {
            final String l = raw.trim();
            final boolean header = isHeader(l);
            if (header || l.startsWith("@;")) {
                if (name != null) out.put(name, entry(width, small, body));
                name = (header) ? l.substring(1) : null;
                width = DEFAULT_WIDTH;
                small = false;
                body.clear();
                continue;
            }
            if (name == null) continue;
            if (l.startsWith("@width=")) {
                try {
                    width = Integer.parseInt(l.substring(7).trim());
                } catch (NumberFormatException e) {
                    width = DEFAULT_WIDTH;
                }
                continue;
            }
            if (l.equals("@SMALLFONT")) {
                small = true;
                continue;
            }
            body.add(raw);
        }
        if (name != null) out.put(name, entry(width, small, body));
        return new ClassicPedia(out);
    }

    /** Whether a trimmed line names a section: '@' and capitals or digits, not a directive. */
    private static boolean isHeader(String l) {
        if (l.length() < 2 || l.charAt(0) != '@' || l.equals("@SMALLFONT")) return false;
        for (int i = 1; i < l.length(); i++) {
            final char c = l.charAt(i);
            if (!(c >= 'A' && c <= 'Z') && !(c >= '0' && c <= '9')) return false;
        }
        return true;
    }

    private static Entry entry(int width, boolean small, List<String> body) {
        final List<String> b = new ArrayList<>(body);
        while (!b.isEmpty() && b.get(b.size() - 1).trim().isEmpty()) {
            b.remove(b.size() - 1);
        }
        return new Entry(width, small, b);
    }

    /**
     * A section.
     *
     * @param section Its name without '@' ({@code FATHER2}).
     * @return The entry, or null.
     */
    Entry entry(String section) {
        return this.sections.get(section);
    }

    /**
     * A category name of {@code @PEDIA} ("Gründerväter" is 5).
     *
     * @param index The 0-based index (blank lines skipped).
     * @return The name, or null.
     */
    String category(int index) {
        final Entry e = entry("PEDIA");
        if (e == null || index < 0) return null;
        int i = 0;
        for (String l : e.lines) {
            if (l.trim().isEmpty()) continue;
            if (i++ == index) return l.trim();
        }
        return null;
    }


    // The father page

    /**
     * The page of a founding father (class comment).
     *
     * @param pedia The Colonopedia.
     * @param text The original's other texts (LABELS, NAMES).
     * @param tiny FONTTINY.
     * @param wood {@code WOODPANL.PIK}, or null (then the page is black).
     * @param n The father's row in NAMES {@code @FATHERS}
     *     ({@link ClassicFathers#index}).
     * @return The 320x200 picture, or null without the entry, the texts or
     *     the font.
     */
    static BufferedImage fatherPage(ClassicPedia pedia, ClassicText text,
                                    ClassicFont tiny, BufferedImage wood, int n) {
        if (pedia == null || text == null || tiny == null) return null;
        final Entry e = pedia.entry("FATHER" + n);
        final String name = ClassicFathers.name(text, n);
        final String header = text.misc(MISC_HEADER);
        final String category = pedia.category(CATEGORY_FATHERS);
        if (e == null || e.lines.isEmpty() || name == null || header == null
            || category == null) return null;
        final BufferedImage img = new BufferedImage(ClassicMenuBox.VW,
            ClassicMenuBox.VH, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        try {
            if (wood != null) g.drawImage(wood, 0, 0, null);
            final int[] ink = ClassicFont.colours(INK), gold = ClassicFont.colours(GOLD);
            final String h2 = "(" + name + ": " + category + ")";
            tiny.draw(g, header, (322 - tiny.stringWidth(header)) / 2, HEADER1_TOP, gold);
            tiny.draw(g, h2, (322 - tiny.stringWidth(h2)) / 2, HEADER2_TOP, gold);
            final int x0 = (321 - e.width) / 2;
            for (Line l : body(e, tiny)) {
                tiny.drawMarked(g, l.text, x0, l.top, ink, gold);
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    // The terrain page (part W, build spec M4.11 target 4)

    /** PEDIA.TXT {@code @PEDIA}: the terrain types' category («Art des Geländes»). */
    static final int CATEGORY_TERRAIN = 2;

    /**
     * LABELS {@code @MISC}: "Experte", "Straße", "Pflügen", "Fluß",
     * "Küste", "Bewegungskosten", "Verteidigungs- oder Geländebonus".
     */
    static final int MISC_EXPERT = 4, MISC_ROAD = 31, MISC_PLOW = 182, MISC_RIVER = 183,
        MISC_COAST = 184, MISC_MOVES = 185, MISC_DEFENCE = 186;

    /**
     * The picture: 48x48 at (9,23) inside two frame lines, 3C2018 outside
     * (x 7 and 58, y 21 and 72), 593424 inside (V #78325, #76922,
     * #121397).
     */
    static final int PICTURE_X = 9, PICTURE_Y = 23, PICTURE_SIZE = 48;

    /** The picture's frame lines. */
    static final int FRAME_OUTER = 0x3C2018, FRAME_INNER = 0x593424;

    /**
     * The rows: the first glyph top and the pitch (27, 47, 67 ...), the
     * work's figure {@code ICONS.SS.(81 + job)} at (63, top - 6), its
     * yield at x 75.
     */
    static final int ROW_TOP = 27, ROW_PITCH = 20, ROW_ICON_X = 63, ROW_ICON_DY = -6,
        ROW_X = 75;

    /** The work's figure: {@code ICONS.SS.(81 + NAMES @JOB row)}. */
    static final int ICON_JOB_BASE = 81;

    /**
     * The gaps of a row: 8 after the yield (and after the movement cost),
     * 2 from the bonus to the resource marker, 18 from the marker to the
     * resource's words, the expert's line 7 lower, under the bonus or 8
     * right of the resource's words.
     */
    static final int ROW_GAP = 8, MARKER_GAP = 2, MARKER_TEXT_DX = 18,
        RESOURCE_EXPERT_DX = 8, EXPERT_DY = 7;

    /**
     * The expert farmer as the original's page writes him: "+3" (V on all
     * three pages).  The rule is +2 in FreeCol and, FreeCol's
     * specification notes, in the original too: the page keeps the
     * original's documentation.
     */
    static final int EXPERT_FARMER_SHOWN = 3;

    /**
     * One row of the terrain page: a tile work's yield, gold (NAMES
     * {@code @JOB} and the type's production for a free colonist), the
     * improvements' bonus and the expert's, green, and the type's resource,
     * gold, after its marker, where it changes the work's goods.
     */
    static final class TerrainRow {

        /** NAMES {@code @JOB} 0-8. */
        final int job;

        /** "Farmer: 3". */
        final String yield;

        /** "Pflügen/Fluß: +1", or null. */
        final String bonus;

        /** "Experte: x2", or null. */
        final String expert;

        /** "Bestes Holz: +4", or null. */
        final String resource;

        /** The resource's {@code PHYS0.SS} marker, or -1. */
        final int marker;

        TerrainRow(int job, String yield, String bonus, String expert,
                   String resource, int marker) {
            this.job = job;
            this.yield = yield;
            this.bonus = bonus;
            this.expert = expert;
            this.resource = resource;
            this.marker = marker;
        }

        @Override
        public String toString() {
            return this.yield + " | " + this.bonus + " | " + this.expert
                + ((this.resource == null) ? "" : " | " + this.resource + " @" + this.marker);
        }
    }

    /**
     * The Colonopedia's page of a terrain type (F1 on the colony screen,
     * build spec M4.11 target 4; V clip 020 #76922 REGENWALD, #78325
     * TROPENWALD from the colony, #121397 LAUBWALD from the map), over
     * {@code WOODPANL.PIK}:
     * <ul>
     *   <li>the two header lines as the father page's, the second
     *       "(<i>NAMES terrain name</i>: Art des Geländes)" (PEDIA
     *       {@code @PEDIA} 2);</li>
     *   <li>the picture ({@link #PICTURE_X}, framed): three by three cells
     *       of the type with a river, a road and the resource
     *       ({@link ClassicTileArt#pediaFrames});</li>
     *   <li>one row per tile work the type yields to a free colonist, in
     *       NAMES {@code @JOB} order ({@link #terrainRows}): the figure,
     *       "Job: n" gold at x 75, 8 px later the improvements' bonus
     *       green; the resource's marker 2 px after the bonus and its
     *       words gold 18 px after the marker; "Experte: ..." green 7 px
     *       lower, under the bonus, or 8 px right of the resource's
     *       words;</li>
     *   <li>20 px under the last row's top "Bewegungskosten: n" green at
     *       x 63 and 8 px after it "Verteidigungs- oder Geländebonus:
     *       +n%";</li>
     *   <li>the entry PEDIA {@code @TERRAINk} as the father page's body,
     *       its title 20 px under that line (127 after four rows, 147
     *       after five).</li>
     * </ul>
     * Only forests are seen: the rows and the picture of the other types
     * follow the same rules (I).
     *
     * @param pedia The Colonopedia.
     * @param text The original's other texts.
     * @param tiny FONTTINY.
     * @param wood {@code WOODPANL.PIK}, or null (then black).
     * @param type The terrain type.
     * @param sprites A pack sprite by its name ("ICONS.SS.081"), or null.
     * @param picture The picture of a type with a resource, or null.
     * @return The 320x200 picture, or null without its texts.
     */
    static BufferedImage terrainPage(ClassicPedia pedia, ClassicText text, ClassicFont tiny,
            BufferedImage wood, TileType type, Function<String, BufferedImage> sprites,
            BiFunction<TileType, ResourceType, BufferedImage> picture) {
        if (pedia == null || text == null || tiny == null || type == null) return null;
        final int k = terrainEntry(type);
        final Entry e = (k < 0) ? null : pedia.entry("TERRAIN" + k);
        final String header = text.misc(MISC_HEADER);
        final String category = pedia.category(CATEGORY_TERRAIN);
        final String name = ClassicHud.terrainName(text, type.getId());
        final String moves = movesText(text, type), defence = defenceText(text, type);
        if (e == null || e.lines.isEmpty() || header == null || category == null
            || name == null || moves == null || defence == null) return null;
        final ResourceType resource = pageResource(type);
        final List<TerrainRow> rows = terrainRows(text, type, resource);
        final BufferedImage img = new BufferedImage(ClassicMenuBox.VW,
            ClassicMenuBox.VH, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        try {
            if (wood != null) g.drawImage(wood, 0, 0, null);
            final int[] ink = ClassicFont.colours(INK), gold = ClassicFont.colours(GOLD);
            final String h2 = "(" + name + ": " + category + ")";
            tiny.draw(g, header, (322 - tiny.stringWidth(header)) / 2, HEADER1_TOP, gold);
            tiny.draw(g, h2, (322 - tiny.stringWidth(h2)) / 2, HEADER2_TOP, gold);
            g.setColor(new Color(FRAME_OUTER));
            g.drawRect(PICTURE_X - 2, PICTURE_Y - 2, PICTURE_SIZE + 3, PICTURE_SIZE + 3);
            g.setColor(new Color(FRAME_INNER));
            g.drawRect(PICTURE_X - 1, PICTURE_Y - 1, PICTURE_SIZE + 1, PICTURE_SIZE + 1);
            final BufferedImage pic = (picture == null) ? null : picture.apply(type, resource);
            if (pic != null) g.drawImage(pic, PICTURE_X, PICTURE_Y, null);
            int top = ROW_TOP;
            for (TerrainRow r : rows) {
                drawSprite(g, sprites, String.format("ICONS.SS.%03d", ICON_JOB_BASE + r.job),
                           ROW_ICON_X, top + ROW_ICON_DY);
                tiny.draw(g, r.yield, ROW_X, top, gold);
                final int bx = ROW_X + tiny.stringWidth(r.yield) + ROW_GAP;
                int ex = bx;
                if (r.bonus != null) tiny.draw(g, r.bonus, bx, top, ink);
                if (r.resource != null) {
                    final int mx = bx + ((r.bonus == null) ? 0 : tiny.stringWidth(r.bonus))
                        + MARKER_GAP;
                    if (r.marker >= 0) {
                        drawSprite(g, sprites, String.format("PHYS0.SS.%03d", r.marker),
                                   mx, top + ROW_ICON_DY);
                    }
                    tiny.draw(g, r.resource, mx + MARKER_TEXT_DX, top, gold);
                    ex = mx + MARKER_TEXT_DX + RESOURCE_EXPERT_DX;
                }
                if (r.expert != null) tiny.draw(g, r.expert, ex, top + EXPERT_DY, ink);
                top += ROW_PITCH;
            }
            tiny.draw(g, moves, ROW_ICON_X, top, ink);
            tiny.draw(g, defence, ROW_ICON_X + tiny.stringWidth(moves) + ROW_GAP, top, ink);
            final int x0 = (321 - e.width) / 2;
            for (Line l : body(e, tiny, top + ROW_PITCH)) {
                tiny.drawMarked(g, l.text, x0, l.top, ink, gold);
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    /** Draw a pack sprite, if there is one, its corner at (x, y). */
    private static void drawSprite(Graphics2D g, Function<String, BufferedImage> sprites,
                                   String name, int x, int y) {
        final BufferedImage s = (sprites == null) ? null : sprites.apply(name);
        if (s != null) g.drawImage(s, x, y, null);
    }

    /**
     * A terrain type's PEDIA.TXT {@code @TERRAINk} (the order of NAMES.TXT's
     * {@code @UNFORESTED} 0-7, {@code @FORESTED} 8-15 and {@code @OTHER}
     * 24-28, as {@link ClassicHud#terrainName}; V: REGENWALD 15,
     * TROPENWÄLDER 13, LAUBWALD 11 by their texts).
     *
     * @param type The type, or null.
     * @return k, or -1.
     */
    static int terrainEntry(TileType type) {
        return (type == null) ? -1 : ClassicHud.terrainRow(type.getId());
    }

    /**
     * The resource a terrain page shows: the type's first (each type of
     * the original has one; FreeCol's mixed forest has two, its furs
     * first: I).
     *
     * @param type The type.
     * @return The resource type, or null.
     */
    static ResourceType pageResource(TileType type) {
        final List<ResourceType> all = (type == null) ? null : type.getResourceTypeValues();
        return (all == null || all.isEmpty()) ? null : all.get(0);
    }

    /**
     * The rows of a terrain page (class comment of {@link #terrainPage}):
     * NAMES {@code @JOB} 0-8 whose goods the type yields a free colonist
     * (the type's production: Tropenwald Farmer 3, Zuckerpflanzer 1,
     * Pelzjäger 2, Holzfäller 4, V #78325); the bonus «Pflügen/Fluß» of
     * the goods the plow raises, «Straße/Fluß» of those the road raises
     * (the river raises both alike), «Küste» the fisherman's (I); the
     * expert's own modifier, "+n" or "xn" (the farmer {@link
     * #EXPERT_FARMER_SHOWN}); the resource where it changes the goods for a
     * free colonist, "+n", "+n/m" when the expert gets m (Wild +2/4,
     * #121397), "xn" for a factor (I).
     *
     * @param t The texts.
     * @param type The terrain type.
     * @param resource The page's resource type, or null.
     * @return The rows, top to bottom.
     */
    static List<TerrainRow> terrainRows(ClassicText t, TileType type, ResourceType resource) {
        final List<TerrainRow> out = new ArrayList<>();
        if (t == null || type == null) return out;
        final Specification spec = type.getSpecification();
        final UnitType colonist = spec.getDefaultUnitType();
        for (int job = 0; job < ClassicColonyLayout.TILE_JOBS; job++) {
            final GoodsType goods = tileGoods(spec, job);
            final String word = ClassicColonyJobs.jobWord(t, job, 0);
            if (goods == null || word == null) continue;
            final int n = type.getPotentialProduction(goods, colonist);
            if (n <= 0) continue;
            final UnitType expert = spec.getExpertForProducing(goods);
            String res = null;
            int marker = -1;
            final String value = (resource == null) ? null
                : resourceBonus(resource, goods.getId(), colonist, expert);
            final String resName = (value == null) ? null : resourceName(t, resource);
            if (resName != null) {
                res = resName + ": " + value;
                marker = ClassicTileArt.resourceFrame(resource.getId());
            }
            out.add(new TerrainRow(job, word + ": " + n, improvementBonus(t, spec, goods.getId()),
                                   expertBonus(t, job, goods, expert), res, marker));
        }
        return out;
    }

    /**
     * The goods of a tile work: the farmer's FreeCol grain (stored as
     * food), the others' NAMES {@code @JOB} goods.
     */
    private static GoodsType tileGoods(Specification spec, int job) {
        if (job == 0) {
            final GoodsType grain = spec.getGoodsType("model.goods.grain");
            if (grain != null) return grain;
        }
        final String id = ClassicColonyLayout.jobGoods(job);
        return (id == null) ? null : spec.getGoodsType(id);
    }

    /** «Pflügen/Fluß: +1», «Straße/Fluß: +2», «Küste: +2» or null. */
    private static String improvementBonus(ClassicText t, Specification spec, String goodsId) {
        final String river = t.misc(MISC_RIVER);
        final int plow = additive(spec.getTileImprovementType("model.improvement.plow"), goodsId);
        if (plow > 0) return (river == null || t.misc(MISC_PLOW) == null) ? null
            : t.misc(MISC_PLOW) + "/" + river + ": +" + plow;
        final int road = additive(spec.getTileImprovementType("model.improvement.road"), goodsId);
        if (road > 0) return (river == null || t.misc(MISC_ROAD) == null) ? null
            : t.misc(MISC_ROAD) + "/" + river + ": +" + road;
        final int coast = additive(spec.getTileImprovementType("model.improvement.fishBonusLand"),
                                   goodsId);
        return (coast > 0 && t.misc(MISC_COAST) != null)
            ? t.misc(MISC_COAST) + ": +" + coast : null;
    }

    /** «Experte: +3», «Experte: x2» or null. */
    private static String expertBonus(ClassicText t, int job, GoodsType goods, UnitType expert) {
        final String word = t.misc(MISC_EXPERT);
        if (word == null || expert == null) return null;
        if (job == 0) return word + ": +" + EXPERT_FARMER_SHOWN;
        float add = 0f, mul = 1f;
        for (Modifier m : toList(expert.getModifiers(goods.getId()))) {
            if (m.getType() == Modifier.ModifierType.ADDITIVE) add += m.getValue();
            if (m.getType() == Modifier.ModifierType.MULTIPLICATIVE) mul *= m.getValue();
        }
        return (add > 0f) ? word + ": +" + number(add)
            : (mul > 1f) ? word + ": x" + number(mul) : null;
    }

    /** A resource's change of a goods: "+n", "+n/m", "xn", or null. */
    private static String resourceBonus(ResourceType resource, String goodsId,
                                        UnitType colonist, UnitType expert) {
        float add = 0f, mul = 1f, addExpert = 0f;
        for (Modifier m : toList(resource.getModifiers(goodsId, colonist))) {
            if (m.getType() == Modifier.ModifierType.ADDITIVE) add += m.getValue();
            if (m.getType() == Modifier.ModifierType.MULTIPLICATIVE) mul *= m.getValue();
        }
        if (expert != null) {
            for (Modifier m : toList(resource.getModifiers(goodsId, expert))) {
                if (m.getType() == Modifier.ModifierType.ADDITIVE) addExpert += m.getValue();
            }
        }
        if (add > 0f) {
            return "+" + number(add) + ((addExpert > add) ? "/" + number(addExpert) : "");
        }
        return (mul > 1f) ? "x" + number(mul) : null;
    }

    /** The sum of an object's additive modifiers of a goods (0 without the object). */
    private static int additive(FreeColObject o, String goodsId) {
        if (o == null) return 0;
        float add = 0f;
        for (Modifier m : toList(o.getModifiers(goodsId))) {
            if (m.getType() == Modifier.ModifierType.ADDITIVE) add += m.getValue();
        }
        return Math.round(add);
    }

    /** A modifier's value as the page writes it: whole, else one decimal. */
    private static String number(float v) {
        return (v == Math.rint(v)) ? Integer.toString(Math.round(v))
            : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** NAMES {@code @RESOURCE} column 0 of a resource type, or null. */
    private static String resourceName(ClassicText t, ResourceType resource) {
        final int row = ClassicHud.resourceRow(resource.getId());
        final List<String[]> rows = t.names("RESOURCE");
        if (row < 0 || rows == null || row >= rows.size()) return null;
        final String s = rows.get(row)[0].trim();
        return (s.isEmpty()) ? null : s;
    }

    /** «Bewegungskosten: 2» (FreeCol's move cost in whole moves), or null. */
    static String movesText(ClassicText t, TileType type) {
        final String w = t.misc(MISC_MOVES);
        return (w == null) ? null : w + ": " + (type.getBasicMoveCost() / 3);
    }

    /** «Verteidigungs- oder Geländebonus: +50%» (FreeCol's defence modifier), or null. */
    static String defenceText(ClassicText t, TileType type) {
        final String w = t.misc(MISC_DEFENCE);
        if (w == null) return null;
        final int d = Math.round(type.apply(100f, null, Modifier.DEFENCE)) - 100;
        return w + ": " + ((d >= 0) ? "+" : "") + d + "%";
    }

    /** One line of a page: its glyph top and its marked text. */
    static final class Line {

        final int top;
        final String text;

        Line(int top, String text) {
            this.top = top;
            this.text = text;
        }

        @Override
        public String toString() {
            return this.top + ":" + this.text;
        }
    }

    /**
     * An entry's lines as the page draws them (class comment): the title,
     * then the paragraphs reflowed, each line opening with '{' when the
     * gold runs on into it.
     *
     * @param e The entry.
     * @param tiny FONTTINY.
     * @return The lines, top to bottom.
     */
    static List<Line> body(Entry e, ClassicFont tiny) {
        return body(e, tiny, TITLE_TOP);
    }

    /**
     * {@link #body(Entry, ClassicFont)} from another glyph top (the
     * terrain page's entry under its rows, part W).
     *
     * @param e The entry.
     * @param tiny FONTTINY.
     * @param top The title's glyph top.
     * @return The lines, top to bottom.
     */
    static List<Line> body(Entry e, ClassicFont tiny, int top) {
        final List<Line> out = new ArrayList<>();
        if (e.lines.isEmpty()) return out;
        final int limit = 320 - 2 * ((321 - e.width) / 2);
        int y = top;
        String title = e.lines.get(0).trim();
        if (title.startsWith("^")) title = title.substring(1);
        out.add(new Line(y, title));
        y += PITCH;
        final StringBuilder sb = new StringBuilder();
        for (int i = 1; i < e.lines.size(); i++) sb.append(e.lines.get(i)).append(' ');
        final String all = sb.toString().replace("%%", "%");
        boolean gold = false, first = true;
        for (String para : all.split("\\^")) {
            final String p = para.replace('\t', ' ').trim();
            if (p.isEmpty()) continue;
            if (!first) y += PITCH;   // one blank line between paragraphs
            first = false;
            String cur = "";
            for (String w : p.split(" +")) {
                final String t = cur.isEmpty() ? w : cur + " " + w;
                if (cur.isEmpty() || tiny.markedWidth(t) <= limit) {
                    cur = t;
                    continue;
                }
                out.add(new Line(y, (gold ? "{" : "") + cur));
                gold = goldAfter(cur, gold);
                y += PITCH;
                cur = w;
            }
            if (!cur.isEmpty()) {
                out.add(new Line(y, (gold ? "{" : "") + cur));
                gold = goldAfter(cur, gold);
                y += PITCH;
            }
        }
        return out;
    }

    /** Whether the gold is on after a line that began with it {@code on}. */
    private static boolean goldAfter(String s, boolean on) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '{') on = true;
            if (s.charAt(i) == '}') on = false;
        }
        return on;
    }


    // The menu's lists (COLONIPÄDIE -> Gründerväter, build spec D8b; I: no recording)

    /** The lists' text width: the father box's {@code @width}. */
    static final int LIST_WIDTH = 230;

    /** The number of father types (NAMES {@code @FOUNDING}). */
    static final int TYPES = 5;

    /**
     * A type's row: NAMES {@code @FOUNDING} and LABELS {@code @MISC} 102,
     * as in the father box ("Handels- Berater").
     *
     * @param t The texts.
     * @param type The type (0 trade ... 4 religion).
     * @return The row, or null.
     */
    static String typeRow(ClassicText t, int type) {
        final List<String[]> types = (t == null) ? null : t.names("FOUNDING");
        final String advisor = (t == null) ? null : t.misc(ClassicFathers.MISC_ADVISOR);
        if (types == null || advisor == null || type < 0 || type >= types.size()) {
            return null;
        }
        return types.get(type)[0] + " " + advisor;
    }

    /**
     * A type's fathers in NAMES {@code @FATHERS} order.
     *
     * @param t The texts.
     * @param type The type.
     * @return Their rows.
     */
    static List<Integer> fathersOfType(ClassicText t, int type) {
        final List<Integer> out = new ArrayList<>();
        for (int n = 0; n < ClassicFathers.IDS.size(); n++) {
            if (ClassicFathers.type(t, n) == type) out.add(n);
        }
        return out;
    }

    /**
     * The first list: the category ("Gründerväter", PEDIA {@code @PEDIA}
     * 5) and one row per type.
     *
     * @param pedia The Colonopedia.
     * @param t The texts.
     * @param bar The barred row.
     * @return The list box, or null without the texts.
     */
    static ClassicAdvisorBox.Request typeList(ClassicPedia pedia, ClassicText t, int bar) {
        final String category = (pedia == null) ? null : pedia.category(CATEGORY_FATHERS);
        if (category == null) return null;
        final List<String> rows = new ArrayList<>();
        for (int i = 0; i < TYPES; i++) {
            final String r = typeRow(t, i);
            if (r == null) return null;
            rows.add(r);
        }
        return list("PEDIA fathers", category, rows, bar, -1.0);
    }

    /**
     * The second list: "Gründerväter (Handels- Berater)" and the type's
     * fathers by their names (NAMES {@code @FATHERS}).
     *
     * @param pedia The Colonopedia.
     * @param t The texts.
     * @param type The type.
     * @param bar The barred row.
     * @return The list box, or null without the texts.
     */
    static ClassicAdvisorBox.Request fatherList(ClassicPedia pedia, ClassicText t,
                                                int type, int bar) {
        return fatherList(pedia, t, type, bar, -1.0);
    }

    /**
     * {@link #fatherList(ClassicPedia, ClassicText, int, int)} asked again
     * after a page.
     *
     * @param pedia The Colonopedia.
     * @param t The texts.
     * @param type The type.
     * @param bar The barred row.
     * @param chainMs The least time after the page (ms), or negative.
     * @return The list box, or null without the texts.
     */
    static ClassicAdvisorBox.Request fatherList(ClassicPedia pedia, ClassicText t,
                                                int type, int bar, double chainMs) {
        final String category = (pedia == null) ? null : pedia.category(CATEGORY_FATHERS);
        final String head = typeRow(t, type);
        if (category == null || head == null) return null;
        final List<String> rows = new ArrayList<>();
        for (int n : fathersOfType(t, type)) {
            final String name = ClassicFathers.name(t, n);
            if (name == null) return null;
            rows.add(name);
        }
        if (rows.isEmpty()) return null;
        return list("PEDIA fathers " + type, category + " (" + head + ")", rows, bar,
                    chainMs);
    }

    /** A list box of the father box's form, Escape and a click beside it close it. */
    private static ClassicAdvisorBox.Request list(String id, String prompt,
                                                  List<String> rows, int bar,
                                                  double chainMs) {
        return ClassicAdvisorBox.Request.builder(id).chain(chainMs)
            .gameText(Collections.singletonList(prompt)).width(LIST_WIDTH)
            .rows(rows).defaultRow(Math.max(0, Math.min(bar, rows.size() - 1)))
            .noCancelRow().rowIndent(ClassicMenuBox.LIST_INDENT)
            .stopgap(prompt, null).build();
    }
}
