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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.ProductionInfo;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;

import static net.sf.freecol.client.gui.classic.ClassicColonyLayout.Slot;
import static net.sf.freecol.client.gui.classic.ClassicColonyLayout.SlotClass;


/**
 * The colony screen's <b>building area</b> in the original's look (build
 * spec M4.2 and M4.3, {@code playthrough-3-analysis/11-spec-M4-colony.md};
 * L §3, dago {@code 01-layout.md} §2 and {@code 01-buildset.md}): the 15
 * fixed slots, each with its family's sprite at the built level or the
 * class's placeholder, which family stands in which slot of a colony, the
 * hover labels with the original's names, the production rows on the
 * buildings, the workers inside them and the units outside the colony on
 * the fortification slot; and the band's figure row (the colonists by
 * their job, then the units outside after a gap).
 *
 * <p>A pure model ({@link #model}) of what is drawn, then one painter
 * ({@link #paint}), so the live screen ({@link ClassicColonyPanel}) and the
 * golden tests draw with the same code; every number comes from
 * {@link ClassicColonyLayout}.  Draw order as the original (V: the five
 * building areas of clip 020 and the three of dago at 0 px): the parchment
 * (the chrome's), the 15 sprites in the slots' order, the production rows
 * with their count labels, the warehouse expansion's «2», the workers with
 * their shadows, the units outside.
 */
final class ClassicColonyBuildings {

    private ClassicColonyBuildings() {}   // static helpers only


    // The families (buildset §2-3, §6)

    /**
     * The 15 drawable families (upgrade chains) of the original, one per
     * slot, with their FreeCol building types from the lowest level.  A
     * FreeCol-only base level ({@code chapel}, {@code depot},
     * {@code country}) draws the placeholder (buildset §6).  Ställe have
     * no slot of their own: they share the Lagerhaus' (047 both, 046
     * Ställe alone; V 047, I 046).
     */
    enum Family {
        FORT(SlotClass.FENCE, false, "stockade", "fort", "fortress"),
        WAFFEN(SlotClass.MID, false, "armory", "magazine", "arsenal"),
        HAFEN(SlotClass.DOCKS, false, "docks", "drydock", "shipyard"),
        RATHAUS(SlotClass.LARGE, true, "townHall"),
        SCHULE(SlotClass.MID, false, "schoolhouse", "college", "university"),
        LAGER(SlotClass.MID, false, "depot", "warehouse", "warehouseExpansion",
              "country", "stables"),
        ZOLL(SlotClass.HOUSE, false, "customHouse"),
        PRESSE(SlotClass.HOUSE, false, "printingPress", "newspaper"),
        WEBER(SlotClass.HOUSE, true, "weaverHouse", "weaverShop", "textileMill"),
        TABAK(SlotClass.HOUSE, true, "tobacconistHouse", "tobacconistShop", "cigarFactory"),
        RUM(SlotClass.HOUSE, true, "distillerHouse", "rumDistillery", "rumFactory"),
        PELZ(SlotClass.HOUSE, true, "furTraderHouse", "furTradingPost", "furFactory"),
        ZIMMEREI(SlotClass.MID, true, "carpenterHouse", "lumberMill"),
        KIRCHE(SlotClass.LARGE, false, "chapel", "church", "cathedral"),
        SCHMIED(SlotClass.HOUSE, true, "blacksmithHouse", "blacksmithShop", "ironWorks");

        /** The slot class. */
        final SlotClass cls;

        /** Whether a new colony has it (the Rathaus, the Zimmerei, the five craft houses; V clip008). */
        final boolean founding;

        /** FreeCol's building types of the chain (short ids). */
        final List<String> types;

        Family(SlotClass cls, boolean founding, String... types) {
            this.cls = cls;
            this.founding = founding;
            this.types = Collections.unmodifiableList(Arrays.asList(types));
        }
    }

    /** The five craft houses of a new colony, in the order the founding layout gives them their slots. */
    static final Family[] CRAFTS = {
        Family.WEBER, Family.TABAK, Family.RUM, Family.PELZ, Family.SCHMIED
    };

    /**
     * A FreeCol building type's family.
     *
     * @param typeId The type's id, with or without {@code model.building.}.
     * @return The family, or null for a type the original has not.
     */
    static Family familyOf(String typeId) {
        final String s = shortId(typeId);
        for (Family f : Family.values()) if (f.types.contains(s)) return f;
        return null;
    }

    /**
     * FreeCol building type to {@code BUILDING.SS} frame (buildset §6; the
     * frame is the NAMES.TXT {@code @BUILDING} row; V for 22 of the 36
     * drawn, among them 015 Lagerhaus, 019 Verlag and 040 Schmiede in clip
     * 020, the rest I by that identity): the FreeCol-only base levels
     * chapel, depot and country draw no sprite of their own (their slot's
     * placeholder); stables 046 alone, 047 with the Lagerhaus
     * ({@link #lagerFrame}); the expansion the Lagerhaus' sprite and its
     * «2».
     */
    private static final Map<String, Integer> FRAMES = new HashMap<>();
    static {
        final Object[][] t = {
            { "stockade", 0 }, { "fort", 1 }, { "fortress", 2 },
            { "armory", 3 }, { "magazine", 4 }, { "arsenal", 5 },
            { "docks", 6 }, { "drydock", 7 }, { "shipyard", 8 },
            { "townHall", 9 },
            { "schoolhouse", 12 }, { "college", 13 }, { "university", 14 },
            { "warehouse", 15 }, { "warehouseExpansion", 15 }, { "stables", 46 },
            { "customHouse", 18 }, { "printingPress", 19 }, { "newspaper", 20 },
            { "weaverHouse", 21 }, { "weaverShop", 22 }, { "textileMill", 23 },
            { "tobacconistHouse", 24 }, { "tobacconistShop", 25 }, { "cigarFactory", 26 },
            { "distillerHouse", 27 }, { "rumDistillery", 28 }, { "rumFactory", 29 },
            { "furTraderHouse", 32 }, { "furTradingPost", 33 }, { "furFactory", 34 },
            { "carpenterHouse", 35 }, { "lumberMill", 36 },
            { "church", 37 }, { "cathedral", 38 },
            { "blacksmithHouse", 39 }, { "blacksmithShop", 40 }, { "ironWorks", 41 },
        };
        for (Object[] e : t) FRAMES.put((String) e[0], (Integer) e[1]);
    }

    /**
     * The {@code BUILDING.SS} frame of a building type alone.
     *
     * @param typeId The type's id, with or without the prefix.
     * @return The frame, or null when it draws its slot's placeholder.
     */
    static Integer frameOf(String typeId) {
        return FRAMES.get(shortId(typeId));
    }

    /** Lagerhaus with Ställe, Ställe alone (buildset §1: V 047, I 046). */
    static final int FRAME_STORE_STABLES = 47, FRAME_STABLES = 46;

    /**
     * The Lagerhaus slot's sprite (buildset §3): 047 with Ställe and a
     * Lagerhaus, 046 Ställe alone, 015 the Lagerhaus alone, else its
     * placeholder.
     *
     * @param store Whether a Lagerhaus (or its expansion) is built.
     * @param stables Whether Ställe are built.
     * @return The frame, or null for the placeholder.
     */
    static Integer lagerFrame(boolean store, boolean stables) {
        if (stables) return (store) ? FRAME_STORE_STABLES : FRAME_STABLES;
        return (store) ? Integer.valueOf(15) : null;
    }

    /** NAMES.TXT {@code @BUILDING} rows of the expansion and the Ställe (their labels). */
    static final int NAMES_EXPANSION = 16, NAMES_STABLES = 17;

    /** LABELS.TXT {@code @MISC} «Bäume» (line 137) and «(Außerhalb der Kolonie)» (line 138). */
    static final int MISC_TREES = 121, MISC_OUTSIDE = 122;


    // Which family stands in which slot (spec M4.2 target 2)

    /**
     * Where a colony's founding families stand: the Rathaus, the Zimmerei
     * and the five craft houses.  The original spreads them differently
     * in every colony (V 7 colonies; no rule found, buildset §2): ours
     * from the colony's id (I), the Rathaus in L1 (V 6 of 7), the Zimmerei
     * in one of the four mid slots, the craft houses in five of the seven
     * house slots.
     */
    static final class Founding {

        /** The founding families' slots. */
        final Map<Family, Slot> slots;

        Founding(Map<Family, Slot> slots) {
            this.slots = Collections.unmodifiableMap(new EnumMap<>(slots));
        }

        /**
         * A colony's layout from its id: a fixed generator (our own
         * Fisher-Yates over {@link java.util.Random}, whose sequence the
         * Java API fixes), so a colony keeps its layout across reloads and
         * versions.
         *
         * @param seed The colony's id number.
         * @return The layout.
         */
        static Founding of(long seed) {
            // The id scrambled first: java.util.Random's first draws of
            // neighbouring seeds are alike.
            final long mixed = (seed + 0x5C0105L) * 0x9E3779B97F4A7C15L;
            final java.util.Random r = new java.util.Random(mixed ^ (mixed >>> 29));
            final Map<Family, Slot> m = new EnumMap<>(Family.class);
            m.put(Family.RATHAUS, ClassicColonyLayout.slot("L1"));
            final List<Slot> mid = slotsOf(SlotClass.MID);
            m.put(Family.ZIMMEREI, mid.get(r.nextInt(mid.size())));
            final List<Slot> houses = slotsOf(SlotClass.HOUSE);
            for (int i = houses.size() - 1; i > 0; i--) {
                Collections.swap(houses, i, r.nextInt(i + 1));
            }
            for (int i = 0; i < CRAFTS.length; i++) m.put(CRAFTS[i], houses.get(i));
            return new Founding(m);
        }

        /**
         * A layout given slot by slot (a clip's colony, the tests).
         *
         * @param rathaus The Rathaus' slot.
         * @param zimmerei The Zimmerei's.
         * @param weber The Haus des Webers'.
         * @param tabak The Haus des Tabakhändlers'.
         * @param rum The Haus des Rumbrenners'.
         * @param pelz The Haus des Gerbers'.
         * @param schmied The Haus des Schmieds'.
         * @return The layout.
         */
        static Founding of(String rathaus, String zimmerei, String weber, String tabak,
                           String rum, String pelz, String schmied) {
            final Map<Family, Slot> m = new EnumMap<>(Family.class);
            m.put(Family.RATHAUS, ClassicColonyLayout.slot(rathaus));
            m.put(Family.ZIMMEREI, ClassicColonyLayout.slot(zimmerei));
            m.put(Family.WEBER, ClassicColonyLayout.slot(weber));
            m.put(Family.TABAK, ClassicColonyLayout.slot(tabak));
            m.put(Family.RUM, ClassicColonyLayout.slot(rum));
            m.put(Family.PELZ, ClassicColonyLayout.slot(pelz));
            m.put(Family.SCHMIED, ClassicColonyLayout.slot(schmied));
            return new Founding(m);
        }
    }

    /** @return The slots of a class, in the draw order. */
    static List<Slot> slotsOf(SlotClass c) {
        final List<Slot> out = new ArrayList<>();
        for (Slot s : ClassicColonyLayout.SLOTS) if (s.cls == c) out.add(s);
        return out;
    }

    /**
     * {@link #assign(Founding, Map, List)} of a colony seen for the first
     * time (nothing stored).
     *
     * @param founding The founding layout.
     * @param later The other families built (drawn), in the order they were made.
     * @return Each family's slot.
     */
    static Map<Family, Slot> assign(Founding founding, List<Family> later) {
        return assign(founding, null, later);
    }

    /**
     * The slot of every family of a colony (spec M4.2 target 2; L §3.2,
     * buildset §2):
     * <ul>
     *   <li>what the colony keeps ({@code Colony.classicSlots}, written at
     *       the family's first showing and saved with the game): a family
     *       keeps its slot across reloads and later completions, an upgrade
     *       its family's (V: Haus des Schmieds 039 to Schmiede 040 in H3,
     *       #73271);</li>
     *   <li>the founding families where {@link Founding} puts them;</li>
     *   <li>the harbour in W, the fortification in X, the Kirche in the
     *       large slot the Rathaus leaves;</li>
     *   <li>each other family when it first shows, in the order its
     *       building was made (FreeCol's id; the Lagerhaus and the Ställe
     *       are their depot's and country's objects from the founding on),
     *       the first free slot of its class in the draw order (V: the
     *       Verlag took H2 with H1 free, #57464; the Lagerhaus M1 with M2
     *       and M3 free, #105544; dago: the Lagerhaus after the College in
     *       M4, M3, M3; I, 5 cases).</li>
     * </ul>
     * A stored entry that does not fit (another class, a slot taken twice)
     * is dropped.
     *
     * @param founding The founding layout.
     * @param stored What the colony keeps, or null.
     * @param later The other families built (drawn), in the order they were made.
     * @return Each family's slot.
     */
    static Map<Family, Slot> assign(Founding founding, Map<Family, Slot> stored,
                                    List<Family> later) {
        final Map<Family, Slot> out = new EnumMap<>(Family.class);
        if (stored != null) {
            for (Map.Entry<Family, Slot> e : stored.entrySet()) {
                final Slot s = e.getValue();
                if (s != null && s.cls == e.getKey().cls && !out.containsValue(s)) {
                    out.put(e.getKey(), s);
                }
            }
        }
        for (Family f : Family.values()) {
            if (!f.founding || out.containsKey(f)) continue;
            final Slot s = founding.slots.get(f);
            if (s != null && !out.containsValue(s)) out.put(f, s);
        }
        free(out, Family.HAFEN);
        free(out, Family.FORT);
        free(out, Family.KIRCHE);
        for (Family f : Family.values()) {
            if (f.founding) free(out, f);       // a founding slot taken by a stored one
        }
        for (Family f : later) free(out, f);
        return out;
    }

    /** Give a family without a slot the first free one of its class. */
    private static void free(Map<Family, Slot> out, Family f) {
        if (out.containsKey(f)) return;
        for (Slot s : slotsOf(f.cls)) {
            if (!out.containsValue(s)) {
                out.put(f, s);
                return;
            }
        }
    }

    /**
     * A colony's kept layout ({@code Colony.classicSlots}).
     *
     * @param s The text, {@code "RATHAUS=L1 ZIMMEREI=M4 ..."}, or null.
     * @return Each family's slot (unknown names left out).
     */
    static Map<Family, Slot> parse(String s) {
        final Map<Family, Slot> out = new EnumMap<>(Family.class);
        if (s == null) return out;
        for (String part : s.trim().split("\\s+")) {
            final int eq = part.indexOf('=');
            if (eq <= 0) continue;
            final Slot slot = ClassicColonyLayout.slot(part.substring(eq + 1));
            Family f = null;
            for (Family x : Family.values()) {
                if (x.name().equals(part.substring(0, eq))) f = x;
            }
            if (f != null && slot != null) out.put(f, slot);
        }
        return out;
    }

    /**
     * A layout as the colony keeps it.
     *
     * @param m Each family's slot.
     * @return The text, the families in their order.
     */
    static String format(Map<Family, Slot> m) {
        final StringBuilder sb = new StringBuilder();
        for (Family f : Family.values()) {
            final Slot s = m.get(f);
            if (s == null) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(f.name()).append('=').append(s.id);
        }
        return sb.toString();
    }


    // The model of what is drawn

    /** A figure: a unit's sprite at a place. */
    static final class Figure {

        /** The unit (null in a model built by hand). */
        final Unit unit;

        /** The sprite's pack key and its ICONS.SS frame (-1 when it is none). */
        final String key;
        final int frame;

        /** Its top-left corner and width. */
        final int x, y, w;

        Figure(Unit unit, String key, int frame, int x, int y, int w) {
            this.unit = unit;
            this.key = key;
            this.frame = frame;
            this.x = x;
            this.y = y;
            this.w = w;
        }

        /** @return Where a press picks it: the sprite's 16 rows. */
        Rectangle bounds() {
            return new Rectangle(this.x, this.y, Math.max(1, this.w), 16);
        }

        @Override
        public String toString() {
            return String.format("%03d@%d,%d", this.frame, this.x, this.y);
        }
    }

    /** One slot as drawn. */
    static final class Placed {

        /** The slot. */
        final Slot slot;

        /** The sprite's frame, and whether it is the class's placeholder. */
        final int frame;
        final boolean placeholder;

        /** The hover label and its box's y. */
        final String label;
        final int labelY;

        /** The building a colonist can be sent to (null for none: a placeholder, no workplaces). */
        final Building target;

        /** Whether the Lagerhaus' «2» is drawn (the expansion). */
        final boolean expansion;

        /** The production row (null for none) and its icon's frame. */
        final ClassicColonyLayout.Row row;
        final int rowIcon;

        /** The workers inside, left to right; on the fortification slot the units outside. */
        final List<Figure> figures;

        Placed(Slot slot, int frame, boolean placeholder, String label, int labelY,
               Building target, boolean expansion, ClassicColonyLayout.Row row, int rowIcon,
               List<Figure> figures) {
            this.slot = slot;
            this.frame = frame;
            this.placeholder = placeholder;
            this.label = label;
            this.labelY = labelY;
            this.target = target;
            this.expansion = expansion;
            this.row = row;
            this.rowIcon = rowIcon;
            this.figures = Collections.unmodifiableList(new ArrayList<>(figures));
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder(this.slot.id).append('=')
                .append(String.format("%03d", this.frame));
            if (this.expansion) sb.append("+2");
            if (this.row != null) sb.append(" row ").append(this.rowIcon).append('x')
                .append(this.row.n);
            if (!this.figures.isEmpty()) sb.append(" fig ").append(this.figures);
            return sb.toString();
        }
    }

    /** The building area of a colony as drawn, the slots in the draw order. */
    static final class Model {

        final List<Placed> slots;

        /** Each family's slot as the colony keeps it ({@link #format}). */
        final String layout;

        Model(List<Placed> slots) {
            this(slots, "");
        }

        Model(List<Placed> slots, String layout) {
            this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
            this.layout = layout;
        }

        /**
         * The slot under a point: the first in the draw order (the one
         * drawn underneath) whose sprite rectangle holds it, as {@link
         * ClassicColonyPanel#slotAt} (V dago 01-interact §2.2: M2 wins in
         * its overlap with the harbour W; clip008: the label does not
         * depend on where in the sprite the pointer is).
         *
         * @param x The x.
         * @param y The y.
         * @return Its index in {@link #slots}, or -1.
         */
        int slotAt(int x, int y) {
            for (int i = 0; i < this.slots.size(); i++) {
                if (this.slots.get(i).slot.bounds().contains(x, y)) return i;
            }
            return -1;
        }

        /** @return Every figure (the workers and the units outside). */
        List<Figure> figures() {
            final List<Figure> out = new ArrayList<>();
            for (Placed p : this.slots) out.addAll(p.figures);
            return out;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            for (Placed p : this.slots) sb.append(' ').append(p);
            return sb.toString().trim();
        }
    }

    /** The pack's sprites the area is drawn from, cached; every lookup may give null. */
    static final class Assets {

        private final ClassicPackFiles pack;

        private final Map<String, BufferedImage> images = new HashMap<>();
        private final Map<String, BufferedImage> shadows = new HashMap<>();

        private Assets(ClassicPackFiles pack) {
            this.pack = pack;
        }

        /**
         * @param pack The pack, or null (nothing drawn but the model).
         * @return The assets.
         */
        static Assets load(ClassicPackFiles pack) {
            return new Assets(pack);
        }

        /** @return A sprite by its pack key (aliases followed), or null. */
        BufferedImage image(String key) {
            if (this.pack == null || key == null) return null;
            synchronized (this.images) {
                if (!this.images.containsKey(key)) {
                    final String k = this.pack.resolve(key);
                    this.images.put(key, (k == null) ? null : this.pack.image(k));
                }
                return this.images.get(key);
            }
        }

        /**
         * A BUILDING.SS frame in the game palette's colours: the pack
         * carries palette index 12 as #FF5555 (10 pixels, in 018, 019, 020,
         * 037, 038), the game palette and every clip #FF0000 (V: the
         * Verlag's bunting at (151,26)/(153,26), #134173; buildset §2's
         * «pack fix»); no other pixel of the set has either colour.
         *
         * @param frame The frame.
         * @return It, or null.
         */
        BufferedImage building(int frame) {
            final String key = ClassicPackFiles.ssKey(String.format("BUILDING.SS.%03d", frame));
            synchronized (this.shadows) {
                final String k = key + "#game";
                if (this.shadows.containsKey(k)) return this.shadows.get(k);
                final BufferedImage s = image(key);
                final int[] pal = (this.pack == null) ? null : this.pack.gamePalette();
                BufferedImage out = s;
                if (s != null && pal != null && pal.length > PACK_RED_INDEX) {
                    final int red = 0xFF000000 | pal[PACK_RED_INDEX];
                    for (int y = 0; y < s.getHeight(); y++) {
                        for (int x = 0; x < s.getWidth(); x++) {
                            final int p = s.getRGB(x, y);
                            if ((p >>> 24) > 128 && (p & 0xFFFFFF) == PACK_RED) {
                                if (out == s) {
                                    out = new BufferedImage(s.getWidth(), s.getHeight(),
                                                            BufferedImage.TYPE_INT_ARGB);
                                    final Graphics2D g = out.createGraphics();
                                    g.drawImage(s, 0, 0, null);
                                    g.dispose();
                                }
                                out.setRGB(x, y, red);
                            }
                        }
                    }
                }
                this.shadows.put(k, out);
                return out;
            }
        }

        /** @return An ICONS.SS frame. */
        BufferedImage icon(int frame) {
            return image(ClassicPackFiles.ssKey(String.format("ICONS.SS.%03d", frame)));
        }

        /**
         * A sprite's silhouette in one colour (a figure's shadow).
         *
         * @param key The sprite's key.
         * @param rgb The colour.
         * @return It, or null.
         */
        BufferedImage silhouette(String key, int rgb) {
            final BufferedImage s = image(key);
            if (s == null) return null;
            final String k = key + "#" + Integer.toHexString(rgb);
            synchronized (this.shadows) {
                BufferedImage out = this.shadows.get(k);
                if (out == null) {
                    out = new BufferedImage(s.getWidth(), s.getHeight(),
                                            BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < s.getHeight(); y++) {
                        for (int x = 0; x < s.getWidth(); x++) {
                            if ((s.getRGB(x, y) >>> 24) > 128) {
                                out.setRGB(x, y, 0xFF000000 | rgb);
                            }
                        }
                    }
                    this.shadows.put(k, out);
                }
                return out;
            }
        }

        /** @return The pack key a key's aliases end in, or null. */
        String resolve(String key) {
            return (this.pack == null) ? null : this.pack.resolve(key);
        }
    }

    /** The pack's colour of palette index 12 in BUILDING.SS, and the index. */
    static final int PACK_RED = 0xFF5555, PACK_RED_INDEX = 12;

    /** The ICONS.SS frame at the end of a pack key ({@code ...ICONS.SS.059}). */
    private static final Pattern ICON_KEY = Pattern.compile("ICONS\\.SS\\.(\\d+)$");

    /**
     * A unit's figure as the colony screen draws it (spec §1.3, L §3.5;
     * dago §2.7): its map sprite, the pack's {@code image.unit.<type>[.<role>]}
     * (V: an expert 81 + his expertise whatever his job, a free colonist
     * 100, a veteran soldier 059, a seasoned scout 060; outside the colony
     * with a role the role's sprite, the veteran soldier as Soldat 102,
     * #6560; a dragoon 076, dago).
     *
     * @param a The assets.
     * @param u The unit.
     * @param withRole Whether its role shows (outside the colony; a
     *     colonist inside has none).
     * @return Its sprite's key (an alias), never null.
     */
    static String figureKey(Assets a, Unit u, boolean withRole) {
        final String base = "image.unit." + u.getType().getId();
        if (withRole && !u.hasDefaultRole()) {
            final String k = base + "." + Role.getRoleIdSuffix(u.getRole().getId());
            if (a.resolve(k) != null) return k;
        }
        return base;
    }

    /** @return The ICONS.SS frame a key ends in, or -1. */
    static int iconFrame(Assets a, String key) {
        final String k = a.resolve(key);
        if (k == null) return -1;
        final Matcher m = ICON_KEY.matcher(k);
        return (m.find()) ? Integer.parseInt(m.group(1)) : -1;
    }

    /** A figure's width: its sprite's, or 8 without the pack. */
    private static int width(Assets a, String key) {
        final BufferedImage s = a.image(key);
        return (s == null) ? 8 : s.getWidth();
    }

    /**
     * The building area of a colony now.
     *
     * @param colony The colony.
     * @param text The pack's texts, or null (FreeCol's names then).
     * @param a The assets.
     * @param founding The founding layout, or null (the colony's own,
     *     {@link Founding#of(long)} from its id).
     * @param outside The units outside the colony in the order they stand
     *     ({@link #outside}).
     * @param order The colonists in the figure row's order ({@link #keepOrder}),
     *     or null: a building's workers stand in that order (I: V #3500, the
     *     Holzfäller before the Meister-Schreiner in the Sägewerk as in the
     *     row, though he came later; #134173, #130343), else in FreeCol's.
     * @return The model.
     */
    static Model model(Colony colony, ClassicText text, Assets a, Founding founding,
                       List<Unit> outside, List<Unit> order) {
        // The buildings by family; the Lagerhaus' slot holds two of FreeCol's.
        final Map<Family, Building> main = new EnumMap<>(Family.class);
        Building store = null, stables = null;
        for (Building b : colony.getBuildings()) {
            final String id = shortId(b.getType().getId());
            final Family f = familyOf(id);
            if (f == null) continue;
            if (f == Family.LAGER) {
                if ("country".equals(id) || "stables".equals(id)) stables = b;
                else store = b;
                continue;
            }
            main.put(f, b);
        }
        final boolean storeBuilt = store != null && frameOf(store.getType().getId()) != null;
        final boolean stablesBuilt = stables != null && frameOf(stables.getType().getId()) != null;
        final boolean expansion = storeBuilt
            && "warehouseExpansion".equals(shortId(store.getType().getId()));

        // The families drawn that take a free slot, in the order they were
        // made (the Lagerhaus' by its depot's or country's object).
        final Map<Family, Integer> builtAt = new EnumMap<>(Family.class);
        for (Map.Entry<Family, Building> e : main.entrySet()) {
            final Family f = e.getKey();
            if (f.founding || f == Family.HAFEN || f == Family.FORT || f == Family.KIRCHE) {
                continue;
            }
            if (frameOf(e.getValue().getType().getId()) != null) {
                builtAt.put(f, e.getValue().getIdNumber());
            }
        }
        if (storeBuilt || stablesBuilt) {
            int id = Integer.MAX_VALUE;
            if (storeBuilt) id = Math.min(id, store.getIdNumber());
            if (stablesBuilt) id = Math.min(id, stables.getIdNumber());
            builtAt.put(Family.LAGER, id);
        }
        final List<Family> laterFamilies = new ArrayList<>(builtAt.keySet());
        laterFamilies.sort((x, y) -> Integer.compare(builtAt.get(x), builtAt.get(y)));
        final Founding layout = (founding != null) ? founding
            : Founding.of(colony.getIdNumber());
        final Map<Family, Slot> where = assign(layout, parse(colony.getClassicSlots()),
                                               laterFamilies);
        final Map<Slot, Family> who = new HashMap<>();
        for (Map.Entry<Family, Slot> e : where.entrySet()) who.put(e.getValue(), e.getKey());

        final List<Placed> out = new ArrayList<>();
        for (Slot s : ClassicColonyLayout.SLOTS) {
            final Family f = who.get(s);
            Integer frame = null;
            Building b = null;
            boolean two = false;
            int namesRow = -1;
            if (f == Family.LAGER) {
                frame = lagerFrame(storeBuilt, stablesBuilt);
                b = (stablesBuilt) ? stables : (storeBuilt) ? store : null;
                two = expansion;
                namesRow = (stablesBuilt) ? NAMES_STABLES : (expansion) ? NAMES_EXPANSION
                    : (storeBuilt) ? 15 : -1;
            } else if (f != null) {
                b = main.get(f);
                if (b != null) {
                    frame = frameOf(b.getType().getId());
                    if (frame != null) namesRow = frame;
                }
            }
            final boolean placeholder = frame == null;
            final int drawn = (placeholder) ? s.cls.placeholder : frame;
            // The label: the level's name; «Bäume» on a placeholder; the
            // fortification slot «<name> (Außerhalb der Kolonie)», the name
            // empty without a fortification: « (Außerhalb der Kolonie)», its
            // box 2 px wider (V: from x 110, clip 020 #6609, #9956, #45968,
            // #74238, #118725, clip008 #7808, #43485; dago «Festung
            // (Außerhalb der Kolonie)» x 83-198; I for Einpfählung and Fort).
            String label;
            if (s.cls == SlotClass.FENCE) {
                final String outsideLabel = misc(text, MISC_OUTSIDE, "(Außerhalb der Kolonie)");
                label = ((placeholder) ? "" : name(text, namesRow, b)) + " " + outsideLabel;
            } else if (placeholder) {
                label = misc(text, MISC_TREES, "Bäume");
            } else {
                label = name(text, namesRow, (f == Family.LAGER && stablesBuilt) ? null : b);
            }
            final int labelY = ClassicColonyLayout.hoverY(s, placeholder);
            // What stands on and in it (M4.3).
            ClassicColonyLayout.Row row = null;
            int icon = -1;
            final List<Figure> figures = new ArrayList<>();
            if (!placeholder && b != null && s.cls != SlotClass.FENCE) {
                final AbstractGoods made = shown(b);
                if (made != null) {
                    icon = ClassicColonyLayout.goodsIcon(made.getType().getId());
                    final BufferedImage img = (icon < 0) ? null : a.icon(icon);
                    row = ClassicColonyLayout.buildingRow(s,
                        f == Family.LAGER && stablesBuilt, made.getAmount(),
                        (img == null) ? 8 : img.getWidth());
                    if (icon < 0) row = null;
                }
                final List<Unit> workers = new ArrayList<>(b.getUnitList());
                if (order != null) {
                    workers.sort((x, y) -> Integer.compare(rank(order, x), rank(order, y)));
                }
                int widest = 1;
                final List<String> keys = new ArrayList<>();
                for (Unit u : workers) {
                    final String k = figureKey(a, u, false);
                    keys.add(k);
                    widest = Math.max(widest, width(a, k));
                }
                final int[] dx = ClassicColonyLayout.workerOffsets(s, workers.size(), widest);
                for (int i = 0; i < workers.size() && i < dx.length; i++) {
                    final String k = keys.get(i);
                    figures.add(new Figure(workers.get(i), k, iconFrame(a, k), s.x + dx[i],
                                           s.y + s.cls.workerY, width(a, k)));
                }
            }
            if (s.cls == SlotClass.FENCE && outside != null && !outside.isEmpty()) {
                int widest = 1;
                final List<String> keys = new ArrayList<>();
                for (Unit u : outside) {
                    final String k = figureKey(a, u, true);
                    keys.add(k);
                    widest = Math.max(widest, width(a, k));
                }
                final int[] xs = ClassicColonyLayout.outsideX(outside.size(), widest);
                for (int i = 0; i < outside.size(); i++) {
                    final String k = keys.get(i);
                    figures.add(new Figure(outside.get(i), k, iconFrame(a, k), xs[i],
                                           ClassicColonyLayout.OUTSIDE_Y, width(a, k)));
                }
            }
            out.add(new Placed(s, drawn, placeholder, label, labelY,
                               (b != null && !placeholder && b.getType().getWorkPlaces() > 0)
                               ? b : null,
                               two && !placeholder, row, icon, figures));
        }
        return new Model(out, format(where));
    }

    /**
     * The building area as it was before a building was completed (the
     * completion dissolve's old picture, build spec M4.10 target 4; V clip
     * 020 #57671: the tree before the Verlag, #73443: the Haus des Schmieds
     * before the Schmiede, #105689: the tree before the Lagerhaus): the
     * slot of the new building shows its old level's sprite (an upgrade,
     * its workers and row kept) or the class's placeholder (a new family:
     * no workers, no row, as a new building has); the Lagerhaus' slot its
     * other family's sprite, the expansion's «2» gone.
     *
     * @param m The building area now.
     * @param built The building type just completed.
     * @return The area before it, or {@code m} when no slot shows it.
     */
    static Model before(Model m, BuildingType built) {
        if (m == null || built == null) return m;
        final String id = shortId(built.getId());
        final Integer frame = frameOf(id);
        final List<Placed> out = new ArrayList<>(m.slots.size());
        boolean changed = false;
        for (Placed p : m.slots) {
            Placed q = p;
            if (!changed && !p.placeholder) {
                if ("warehouseExpansion".equals(id) && p.expansion) {
                    q = new Placed(p.slot, p.frame, false, p.label, p.labelY, p.target, false,
                                   p.row, p.rowIcon, p.figures);
                } else if ("warehouse".equals(id) && (p.frame == 15
                           || p.frame == FRAME_STORE_STABLES)) {
                    q = (p.frame == FRAME_STORE_STABLES)
                        ? new Placed(p.slot, FRAME_STABLES, false, p.label, p.labelY, p.target,
                                     false, p.row, p.rowIcon, p.figures)
                        : placeholderOf(p);
                } else if (("stables".equals(id) || "country".equals(id))
                           && (p.frame == FRAME_STABLES || p.frame == FRAME_STORE_STABLES)) {
                    q = (p.frame == FRAME_STORE_STABLES)
                        ? new Placed(p.slot, 15, false, p.label, p.labelY, p.target,
                                     p.expansion, p.row, p.rowIcon, p.figures)
                        : placeholderOf(p);
                } else if (frame != null && p.frame == frame
                           && p.slot.cls != SlotClass.FENCE) {
                    final BuildingType from = built.getUpgradesFrom();
                    final Integer old = (from == null) ? null : frameOf(from.getId());
                    q = (old == null) ? placeholderOf(p)
                        : new Placed(p.slot, old, false, p.label, p.labelY, p.target,
                                     p.expansion, p.row, p.rowIcon, p.figures);
                }
            }
            changed |= q != p;
            out.add(q);
        }
        return (changed) ? new Model(out, m.layout) : m;
    }

    /**
     * The building area with another state's production rows (the
     * completion dissolve's old picture, M4.10 target 4: the Rathaus' 4
     * bells before the Verlag's 6, V clip 020 #57671 -&gt; #57768): a slot
     * that shows the same sprite takes the copy's row where the copy
     * without the new building makes another row than the copy with it;
     * the sprites, labels, figures and every other row stay.
     *
     * @param m The building area to show.
     * @param before The building area of a copy of the colony without the
     *     new building.
     * @param plain The building area of a copy with it.
     * @return The area with those rows, or {@code m} when nothing changes.
     */
    static Model withRows(Model m, Model before, Model plain) {
        if (m == null || before == null || plain == null) return m;
        final List<Placed> out = new ArrayList<>(m.slots.size());
        boolean changed = false;
        for (Placed p : m.slots) {
            Placed q = p;
            final Placed o = (p.placeholder) ? null : sameSlot(before, p);
            final Placed w = (o == null) ? null : sameSlot(plain, p);
            if (o != null && w != null
                && (!String.valueOf(o.row).equals(String.valueOf(w.row)) || o.rowIcon != w.rowIcon)) {
                q = new Placed(p.slot, p.frame, false, p.label, p.labelY, p.target,
                               p.expansion, o.row, o.rowIcon, p.figures);
            }
            changed |= q != p;
            out.add(q);
        }
        return (changed) ? new Model(out, m.layout) : m;
    }

    /** @return The slot of an area showing the same sprite as {@code p} there, or null. */
    private static Placed sameSlot(Model m, Placed p) {
        for (Placed o : m.slots) {
            if (!o.placeholder && o.slot.id.equals(p.slot.id) && o.frame == p.frame) return o;
        }
        return null;
    }

    /** A slot showing its class's placeholder: no building, no workers, no row. */
    private static Placed placeholderOf(Placed p) {
        return new Placed(p.slot, p.slot.cls.placeholder, true, p.label, p.labelY, null,
                          false, null, -1, new ArrayList<>());
    }

    /** A unit's place in a list, the end when it is not in it. */
    private static int rank(List<Unit> order, Unit u) {
        final int i = order.indexOf(u);
        return (i < 0) ? Integer.MAX_VALUE : i;
    }

    /**
     * What a building's row shows (L §3.4): its output, the most it can
     * make with its workers even when an input is short (clip008: the
     * Zimmerei's 3 hammers without lumber; FreeCol's maximum production),
     * else what it makes; nothing when that is 0 (V: no row on a building
     * without workers, the Rathaus' 1 bell of its own).
     *
     * @param b The building.
     * @return The good and its amount, or null.
     */
    static AbstractGoods shown(Building b) {
        final ProductionInfo pi = b.getProductionInfo();
        if (pi == null) return null;
        for (AbstractGoods ag : pi.getProduction()) {
            int n = ag.getAmount();
            for (AbstractGoods max : pi.getMaximumProduction()) {
                if (max.getType() == ag.getType()) n = Math.max(n, max.getAmount());
            }
            if (n > 0) return new AbstractGoods(ag.getType(), n);
        }
        return null;
    }

    /** A NAMES.TXT {@code @BUILDING} name, else FreeCol's. */
    private static String name(ClassicText text, int row, Building b) {
        if (text != null && row >= 0) {
            final List<String[]> rows = text.names("BUILDING");
            if (row < rows.size() && rows.get(row).length > 0 && !rows.get(row)[0].isEmpty()) {
                return rows.get(row)[0];
            }
        }
        return (b == null) ? "" : Messages.getName(b.getType());
    }

    private static String misc(ClassicText text, int index, String fallback) {
        final String s = (text == null) ? null : text.misc(index);
        return (s == null) ? fallback : s.trim();
    }


    // The units outside and the figure row

    /**
     * NAMES {@code @JOB}'s index of a unit's work now (L §5.2): what its
     * building or tile makes, a teacher 18; else (outside the colony) its
     * figure's: the role's job (Pionier 20, Soldat 21, Späher 22, Dragoner
     * 23, Missionar 24), else its expertise (the expert's sprite 81 + job),
     * a free colonist Siedler 19.
     *
     * @param a The assets.
     * @param u The unit.
     * @return The index.
     */
    static int jobIndex(Assets a, Unit u) {
        final Object loc = u.getLocation();
        if (loc instanceof Building) {
            final Building b = (Building) loc;
            if (b.canTeach()) return ClassicColonyLayout.JOB_TEACHER;
            final AbstractGoods made = firstOutput(b);
            final int j = (made == null) ? -1
                : ClassicColonyLayout.jobOf(made.getType().getId());
            if (j >= 0) return j;
        } else if (loc instanceof ColonyTile) {
            final GoodsType wt = u.getWorkType();
            final int j = (wt == null) ? -1 : ClassicColonyLayout.jobOf(wt.getId());
            if (j >= 0) return j;
        }
        final int f = iconFrame(a, figureKey(a, u, true));
        if (f >= 81 && f <= 107) return f - 81;
        switch (f) {
        case 58: case 73: return ClassicColonyLayout.JOB_PIONEER;
        case 59: case 74: return ClassicColonyLayout.JOB_SOLDIER;
        case 60: case 75: return ClassicColonyLayout.JOB_SCOUT;
        case 76: return ClassicColonyLayout.JOB_DRAGOON;
        case 61: case 77: return ClassicColonyLayout.JOB_MISSIONARY;
        default: return ClassicColonyLayout.JOB_COLONIST;
        }
    }

    private static AbstractGoods firstOutput(Building b) {
        final List<AbstractGoods> outs = new ArrayList<>();
        b.getOutputs().forEach(outs::add);
        return (outs.isEmpty()) ? null : outs.get(0);
    }

    /**
     * The units outside the colony (L §3.6, §5.2): the persons on its tile
     * that work in none of its places (V: soldiers, free colonists,
     * scouts, pioneers); no ship (the port panel's), no wagon train (a
     * carrier, M4.6), no artillery or treasure (I: the original's fence
     * and figure row show people).  In the order they stand: by their
     * figure's job (I: V for 3 at #46039, a fisherman, a distiller and a
     * blacksmith in that order on the fence and in the figure row), ties
     * in the given order.
     *
     * @param a The assets.
     * @param colony The colony.
     * @param order The tile's units in the order the screen keeps them
     *     (the first slice's row, {@link ClassicColonyPanel.Units#order}),
     *     or null for the tile's.
     * @return The units.
     */
    static List<Unit> outside(Assets a, Colony colony, Function<List<Unit>, List<Unit>> order) {
        final Tile tile = colony.getTile();
        final List<Unit> standing = new ArrayList<>();
        if (tile == null) return standing;
        for (Unit u : tile.getUnitList()) {
            if (!u.isNaval() && u.isPerson()) standing.add(u);
        }
        final List<Unit> ordered = new ArrayList<>((order == null) ? standing
                                                   : order.apply(standing));
        ordered.retainAll(standing);
        final Map<Unit, Integer> key = new HashMap<>();
        for (Unit u : ordered) key.put(u, jobIndex(a, u));
        ordered.sort((x, y) -> Integer.compare(key.get(x), key.get(y)));   // stable
        return ordered;
    }

    /**
     * The colony's colonists by their job now (NAMES {@code @JOB}; L §5.2,
     * V #2260, #134173: within one job the colony's order, I, «082 before
     * 090»).
     *
     * @param a The assets.
     * @param colony The colony.
     * @return The colonists.
     */
    static List<Unit> colonistsByJob(Assets a, Colony colony) {
        final List<Unit> inside = new ArrayList<>(colony.getUnitList());
        final Map<Unit, Integer> key = new HashMap<>();
        for (Unit u : inside) key.put(u, jobIndex(a, u));
        inside.sort((x, y) -> Integer.compare(key.get(x), key.get(y)));   // stable
        return inside;
    }

    /**
     * The figure row's order during a visit (V clip 020: a colonist whose
     * job changed keeps his place, #3500 the Holzfäller made Schreiner
     * before the Schnapsbrenner, #130343 the sugar planter made
     * Schnapsbrenner second; one who joined goes to the colonists' end,
     * #46350 and #47291 the fisherman, the distiller and the blacksmith
     * from the fence after the Staatsmann; by the next turn the row is by
     * job again, #134173): the colonists already in the row keep their
     * order, the others follow in theirs.
     *
     * @param before The row's colonists so far (null: none).
     * @param now The colonists now, by job.
     * @return The row's colonists.
     */
    static List<Unit> keepOrder(List<Unit> before, List<Unit> now) {
        final List<Unit> out = new ArrayList<>();
        if (before != null) for (Unit u : before) if (now.contains(u)) out.add(u);
        for (Unit u : now) if (!out.contains(u)) out.add(u);
        return out;
    }

    /**
     * The band's figure row (L §5.2, dago §4.2; the look of M4.5 target 2):
     * the colonists in their row's order ({@link #colonistsByJob},
     * {@link #keepOrder}), then the units outside after 4 px more, at y 142
     * without shadow, the pitch w + 2 closing as the row fills.
     *
     * @param a The assets.
     * @param colonists The colonists in the row's order.
     * @param outside The units outside ({@link #outside}).
     * @return The figures, the colonists first.
     */
    static List<Figure> figureRow(Assets a, List<Unit> colonists, List<Unit> outside) {
        final List<Unit> inside = new ArrayList<>(colonists);
        final List<Unit> all = new ArrayList<>(inside);
        if (outside != null) all.addAll(outside);
        final int[] widths = new int[all.size()];
        final String[] keys = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            keys[i] = figureKey(a, all.get(i), i >= inside.size());
            widths[i] = width(a, keys[i]);
        }
        final int[] xs = ClassicColonyLayout.figureRow(widths, inside.size());
        final List<Figure> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            out.add(new Figure(all.get(i), keys[i], iconFrame(a, keys[i]), xs[i],
                               ClassicColonyLayout.FIGURE_ROW_Y, widths[i]));
        }
        return out;
    }


    // Painting

    /**
     * The building area as the model says (the parchment is the chrome's,
     * {@link ClassicColonyChrome#paintGround}): the 15 sprites, the rows
     * and their count labels (when the colony report's «Beschriftung an
     * Gebäuden» is on: I, the original's option), the «2», the workers and
     * the units outside with their shadows, the selected one's green frame
     * right after it.
     *
     * @param g The canvas, clipped to the building area.
     * @param c The chrome's assets (the font, the inks).
     * @param a The sprites.
     * @param m The model.
     * @param selected The selected unit, or null.
     * @param labels Whether the stacks carry their count labels.
     */
    static void paint(Graphics2D g, ClassicColonyChrome.Assets c, Assets a, Model m,
                      Unit selected, boolean labels) {
        for (Placed p : m.slots) {
            final BufferedImage s = a.building(p.frame);
            if (s != null) g.drawImage(s, p.slot.x, p.slot.y, null);
        }
        for (Placed p : m.slots) {
            if (p.row == null) continue;
            final BufferedImage icon = a.icon(p.rowIcon);
            if (icon == null) continue;
            for (int j = 0; j < p.row.m; j++) g.drawImage(icon, p.row.x(j), p.row.y, null);
            if (p.row.stacked && labels) {
                countLabel(g, c, String.valueOf(p.row.n), p.row.labelX(), p.row.labelY());
            }
        }
        for (Placed p : m.slots) {
            if (p.expansion) {
                ClassicColonyChrome.text(g, c, "2", p.slot.x + ClassicColonyLayout.EXPANSION_DX,
                    p.slot.y + ClassicColonyLayout.EXPANSION_DY, ClassicColonyLayout.INK_WHITE);
            }
        }
        final int shadow = c.rgb(ClassicColonyLayout.INK_SHADOW);
        for (Placed p : m.slots) {
            for (Figure f : p.figures) {
                figure(g, a, f, shadow);
                if (selected != null && f.unit == selected) {
                    frame(g, c, ClassicColonyLayout.workerFrame(f.x, f.y, f.w));
                }
            }
        }
    }

    /**
     * The band's figure row ({@link #figureRow}): the sprites without
     * shadow, the selected one's green frame x - 1 ... x + w, y 143-158
     * right after it (V).
     *
     * @param g The canvas.
     * @param c The chrome's assets.
     * @param a The sprites.
     * @param row The figures.
     * @param selected The selected unit, or null.
     */
    static void paintFigureRow(Graphics2D g, ClassicColonyChrome.Assets c, Assets a,
                               List<Figure> row, Unit selected) {
        paintFigureRow(g, c, a, row, selected, false, false);
    }

    /**
     * The band's figure row with the white cursor (M4.5 target 2): where
     * the cursor stands on the selected one's figure, its frame is the
     * cursor's, white (ink 15) in the blink's on half and nothing in its off
     * half, no green under it (V clip 020 #123003-#123095, #6540-#6600,
     * #2620, #35057, #46350, #47291); elsewhere the green frame.
     *
     * @param g The canvas.
     * @param c The chrome's assets.
     * @param a The sprites.
     * @param row The figures.
     * @param selected The selected unit, or null.
     * @param cursorOnSelected Whether the cursor stands on the selected one's figure.
     * @param cursorShown Whether the cursor is in its on half.
     */
    static void paintFigureRow(Graphics2D g, ClassicColonyChrome.Assets c, Assets a,
                               List<Figure> row, Unit selected, boolean cursorOnSelected,
                               boolean cursorShown) {
        for (Figure f : row) {
            figure(g, a, f, -1);
            if (selected == null || f.unit != selected) continue;
            if (!cursorOnSelected) {
                frame(g, c, ClassicColonyLayout.figureFrame(f.x, f.w));
            } else if (cursorShown) {
                frame(g, c, ClassicColonyLayout.figureCursor(f.x, f.w),
                      ClassicColonyLayout.INK_WHITE);
            }
        }
    }

    /** A figure, with its shadow in a colour (-1: none). */
    private static void figure(Graphics2D g, Assets a, Figure f, int shadow) {
        final BufferedImage s = a.image(f.key);
        if (s == null) return;
        if (shadow >= 0) {
            final BufferedImage sh = a.silhouette(f.key, shadow);
            if (sh != null) g.drawImage(sh, f.x + ClassicColonyLayout.SHADOW_DX, f.y, null);
        }
        g.drawImage(s, f.x, f.y, null);
    }

    /** A 1-px green frame (ink 10). */
    private static void frame(Graphics2D g, ClassicColonyChrome.Assets c, Rectangle r) {
        frame(g, c, r, ClassicColonyLayout.INK_GREEN);
    }

    /** A 1-px frame in an ink. */
    private static void frame(Graphics2D g, ClassicColonyChrome.Assets c, Rectangle r, int ink) {
        g.setColor(new Color(c.rgb(ink)));
        g.fillRect(r.x, r.y, r.width, 1);
        g.fillRect(r.x, r.y + r.height - 1, r.width, 1);
        g.fillRect(r.x, r.y, 1, r.height);
        g.fillRect(r.x + r.width - 1, r.y, 1, r.height);
    }

    /**
     * A stack's count label (dago §2.6): a black (w + 1) x 7 box, the
     * number white one pixel in.
     *
     * @param g The canvas.
     * @param c The chrome's assets.
     * @param s The number.
     * @param x The box's x.
     * @param y The box's y.
     */
    static void countLabel(Graphics2D g, ClassicColonyChrome.Assets c, String s, int x, int y) {
        final int w = ClassicColonyChrome.textWidth(c, s);
        g.setColor(new Color(c.rgb(ClassicColonyLayout.INK_BLACK)));
        g.fillRect(x, y, w + 1, ClassicColonyLayout.HOVER_H);
        ClassicColonyChrome.text(g, c, s, x + 1, y + 1, ClassicColonyLayout.INK_WHITE);
    }

    /** The last segment of a FreeCol id ({@code model.building.townHall} to {@code townHall}). */
    static String shortId(String id) {
        if (id == null) return "";
        final int i = id.lastIndexOf('.');
        return (i < 0) ? id : id.substring(i + 1);
    }

    /** @return A building type's frame for a type object (tests, the recorder). */
    static Integer frameOf(BuildingType bt) {
        return (bt == null) ? null : frameOf(bt.getId());
    }
}
