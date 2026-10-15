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
import java.util.Collections;
import java.util.List;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitLocation.NoAddReason;
import net.sf.freecol.common.model.UnitType;


/**
 * The colony screen's <b>job menus</b> (build spec M4.8 targets 3-6; the
 * colony's untagged boxes of clip 020, {@code 10-dialog-catalogue.md} §3,
 * {@code 02-colony-operations.md} §3-5, dago-colony2 {@code 01-base.md}
 * §3, clip008 §3.5): what each of them lists, the right column, the
 * colours, the bar, and what a pick does.  Headless; the screen turns a
 * {@link Menu} into a list box ({@link #request}) and acts on the pick
 * ({@code ClassicColonyPanel}).
 *
 * <ul>
 *   <li><b>J2</b> ({@link Kind#WORKER}; V ×28): a release without motion on
 *       the selected colonist at work.  Title LABELS @CTITLE 8 «Beruf
 *       wählen für» + his type's NAMES {@code @JOB} plural + «(job)» only
 *       when he does not work in his own speciality («Erfahrene
 *       Holzfäller(Farmer):» #13, «Meister-Zuckerpflanzer:» #104), flowed
 *       to two lines when long (#42); «(SPEZIALFÄHIGKEIT LÖSCHEN)» first,
 *       only for an expert; the works of NAMES {@code @JOB} 0-18 the colony
 *       offers, the first 16, then «(Weitere)»; the footer «(F1 für
 *       Hilfe)»; x 62, w 196, h = 18 + 6·lines + 8·rows + 6, centred.</li>
 *   <li><b>The page «(Weitere)»</b> (V #18, #254, dago-colony2 #7917): the
 *       same title; «(Weitere)», the rest of the works, then the roles
 *       Siedler, Pionier «(n Werkzeuge)», Soldat «(50 Musketen)», Späher
 *       «(50 Pferde)», Dragoner «(50 Musketen) (50 Pferde)» and Missionar
 *       (only with a church: dago's Base had one, clip 020's Base none);
 *       a role the warehouse cannot equip is grey.  «(Weitere)» there
 *       leads back (V clip008 J3).</li>
 *   <li><b>J1</b> ({@link Kind#FENCE_DROP}; V #16, #77, dago #2500,
 *       dago-colony2 #8986): a colonist dropped on the fence: his job
 *       (without a cell), then the roles; no «LÖSCHEN», no «(Weitere)».</li>
 *   <li><b>A unit at the fence</b> ({@link Kind#OUTSIDE}; V #96, #97,
 *       #253): «Beruf wählen für &lt;Typ&gt;:» with no «(job)», every right
 *       cell «n Ware», «LÖSCHEN» only for an expert, the bar on row 1; its
 *       «(Weitere)» page has the bar on his role (#254: «Siedler»).</li>
 * </ul>
 *
 * <p><b>Right column</b> (V, all 28 menus): for a tile worker the tile
 * works read «a/b Ware», a his yield on his own tile, b the best of his
 * tile and the colony's free tiles (I: #162 «8/10 Nutzholz» on W with N
 * free at 10, #103 «0/2 Zucker»); everyone else's tile works «b Ware»
 * (the free tiles only), the building works «n Ware», n what he would
 * make in that building (FreeCol's potential production: its numbers,
 * not the original's, where the two differ).  The fisherman's good is
 * «Nahrungsmittel» (#13).  <b>Yellow</b>: the expert's own work or role
 * with its cell (#13 Holzfäller, #16 Soldat, #77 Späher).  <b>The
 * bar</b>: on his work where the page lists it, else on row 1 (#13, #96,
 * #253, #254; dago-colony2: «(Weitere)» on page 2, his job not there).
 */
final class ClassicColonyJobs {

    /** What a menu is (class comment). */
    enum Kind {
        /** J2: the selected colonist at work, clicked. */
        WORKER,
        /** A unit standing at the fence, clicked. */
        OUTSIDE,
        /** J1: a colonist dropped on the fence. */
        FENCE_DROP
    }

    /** What a row does. */
    enum Pick {
        /** «(SPEZIALFÄHIGKEIT LÖSCHEN)»: @LOBOTOMIZE, the speciality cleared. */
        CLEAR,
        /** A work: a tile's (on his tile, or the best free one) or a building's. */
        JOB,
        /** «(Weitere)»: the other page. */
        MORE,
        /** A role: outside the colony with the role's goods. */
        ROLE,
        /** J1's first row: his work as it is (nothing changes). */
        STAY
    }

    /** The works a page 1 lists at most (V: 16 in 30 menus, dago-colony2 §3.2). */
    static final int PAGE_JOBS = 16;

    /** LABELS @CTITLE: «(Weitere)» 6, «Beruf wählen für» 8. */
    static final int CTITLE_MORE = 6, CTITLE_CHOOSE = 8;

    /** LABELS @MISC 43: «(SPEZIALFÄHIGKEIT LÖSCHEN)». */
    static final int MISC_CLEAR = 43;

    /** The job menus' text width: 190 (the box 196, V). */
    static final int WIDTH = 190;

    /** NAMES {@code @CARGO}'s row of each warehouse good, by FreeCol id (the right column's words). */
    private static final String[] CARGO = {
        "model.goods.food", "model.goods.sugar", "model.goods.tobacco",
        "model.goods.cotton", "model.goods.furs", "model.goods.lumber",
        "model.goods.ore", "model.goods.silver", "model.goods.horses",
        "model.goods.rum", "model.goods.cigars", "model.goods.cloth",
        "model.goods.coats", "model.goods.tradeGoods", "model.goods.tools",
        "model.goods.muskets", "model.goods.hammers", "model.goods.crosses",
        "model.goods.bells",
    };

    /** The roles of NAMES {@code @JOB} 19-24, FreeCol's ids. */
    private static final String[] ROLES = {
        "model.role.default", "model.role.pioneer", "model.role.soldier",
        "model.role.scout", "model.role.dragoon", "model.role.missionary",
    };

    /** One row of a menu. */
    static final class Row {

        /** What it does. */
        final Pick pick;

        /** Its NAMES {@code @JOB} index (a work, a role, J1's own work), -1 else. */
        final int job;

        /** Its words (markup-free), and its right cell or null. */
        final String label, cell;

        /** Yellow (the expert's own), grey (the warehouse cannot equip it). */
        final boolean yellow, grey;

        /** A role's count (the pioneer's tools in twenties), 0 else. */
        final int count;

        Row(Pick pick, int job, String label, String cell, boolean yellow, boolean grey,
            int count) {
            this.pick = pick;
            this.job = job;
            this.label = label;
            this.cell = cell;
            this.yellow = yellow;
            this.grey = grey;
            this.count = count;
        }

        @Override
        public String toString() {
            return ((this.yellow) ? "{" : "") + ((this.grey) ? "<" : "") + this.label
                + ((this.cell == null) ? "" : "  " + this.cell) + ((this.grey) ? ">" : "")
                + ((this.yellow) ? "}" : "");
        }
    }

    /** A menu: its title, its rows, the bar's row. */
    static final class Menu {

        final Kind kind;

        /** 1 or 2 («(Weitere)»); J1 is 1. */
        final int page;

        final String title;

        final List<Row> rows;

        /** The bar's row at the opening. */
        final int bar;

        Menu(Kind kind, int page, String title, List<Row> rows, int bar) {
            this.kind = kind;
            this.page = page;
            this.title = title;
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
            this.bar = bar;
        }

        /**
         * @param pick What a row does.
         * @param job Its job, or -1 for any.
         * @return The first such row's index, or -1.
         */
        int rowOf(Pick pick, int job) {
            for (int i = 0; i < this.rows.size(); i++) {
                final Row r = this.rows.get(i);
                if (r.pick == pick && (job < 0 || r.job == job)) return i;
            }
            return -1;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder(this.title);
            for (int i = 0; i < this.rows.size(); i++) {
                sb.append(" / ").append((i == this.bar) ? "[" : "").append(this.rows.get(i))
                    .append((i == this.bar) ? "]" : "");
            }
            return sb.toString();
        }
    }


    private ClassicColonyJobs() {}   // static helpers only


    /**
     * The pioneer's tools from a warehouse (V: 75 → «(60 Werkzeuge)», 75
     * → 15 at #121100; dago-colony2: 0 → «(20 …)» grey, 44 → 40, ≥ 100 →
     * 100): steps of 20, at least 20, at most 100.
     *
     * @param tools The warehouse's tools.
     * @return The amount offered.
     */
    static int pioneerTools(int tools) {
        return Math.max(20, Math.min(100, 20 * (Math.max(0, tools) / 20)));
    }

    /**
     * A unit's work now as a NAMES {@code @JOB} index: in a building or on
     * a tile the work's (a teacher 18); outside the colony its role's
     * (Siedler 19 for none).
     *
     * @param u The unit.
     * @return The index.
     */
    static int currentJob(Unit u) {
        final Object loc = u.getLocation();
        if (loc instanceof Building && ((Building) loc).canTeach()) {
            return ClassicColonyLayout.JOB_TEACHER;
        }
        if (loc instanceof Building || loc instanceof ColonyTile) {
            final GoodsType wt = u.getWorkType();
            final int j = (wt == null) ? -1 : ClassicColonyLayout.jobOf(wt.getId());
            if (j >= 0) return j;
            if (loc instanceof Building) {
                for (AbstractGoods ag : (Iterable<AbstractGoods>) ((Building) loc)
                         .getOutputs()::iterator) {
                    final int k = ClassicColonyLayout.jobOf(ag.getType().getId());
                    if (k >= 0) return k;
                }
            }
            return 0;
        }
        return roleJob(u.getRole());
    }

    /**
     * @param r A role, or null.
     * @return Its NAMES {@code @JOB} index (Siedler 19 for the default).
     */
    static int roleJob(Role r) {
        final String id = (r == null) ? null : r.getId();
        for (int i = 0; i < ROLES.length; i++) {
            if (ROLES[i].equals(id)) return ClassicColonyLayout.JOB_COLONIST + i;
        }
        return ClassicColonyLayout.JOB_COLONIST;
    }

    /**
     * @param spec The rules.
     * @param job A role's NAMES {@code @JOB} index, 19-24.
     * @return The role, or null.
     */
    static Role roleOf(Specification spec, int job) {
        final int i = job - ClassicColonyLayout.JOB_COLONIST;
        return (i < 0 || i >= ROLES.length) ? null : spec.getRole(ROLES[i]);
    }

    /**
     * The expert's own work or role as a NAMES {@code @JOB} index (his
     * type's row: Erfahrene Holzfäller 5, Erfahrene Soldaten 21,
     * Kampferprobte Späher 22), -1 for a free colonist, a servant, a
     * criminal, a convert.
     *
     * @param u The unit.
     * @return The index, or -1.
     */
    static int expertJob(Unit u) {
        final int row = ClassicHud.jobRow(ClassicColonyBuildings.shortId(u.getType().getId()));
        return (row >= 0 && row <= 17) || (row >= ClassicColonyLayout.JOB_PIONEER
                                           && row <= ClassicColonyLayout.JOB_MISSIONARY)
            ? row : -1;
    }

    /**
     * The good of a work 0-17 in these rules: the Farmer's the tiles'
     * grain (stored as food, both rules), else food.
     *
     * @param spec The rules.
     * @param job The index.
     * @return The good, or null.
     */
    static GoodsType goodsOf(Specification spec, int job) {
        if (job == 0) {
            for (String id : new String[] { "model.goods.grain", "model.goods.food" }) {
                final GoodsType g = spec.getGoodsType(id);
                if (g != null && g.isFarmed()) return g;
            }
            return spec.getGoodsType("model.goods.food");
        }
        final String id = ClassicColonyLayout.jobGoods(job);
        return (id == null) ? null : spec.getGoodsType(id);
    }

    /**
     * NAMES {@code @CARGO}'s word of a good (the right column; fish and
     * FreeCol's grain are «Nahrungsmittel»).
     *
     * @param t The texts.
     * @param g The good.
     * @return The word, or FreeCol's name.
     */
    static String cargoName(ClassicText t, GoodsType g) {
        String id = g.getId();
        if ("model.goods.fish".equals(id) || "model.goods.grain".equals(id)) {
            id = "model.goods.food";
        }
        final List<String[]> rows = (t == null) ? null : t.names("CARGO");
        for (int i = 0; rows != null && i < CARGO.length && i < rows.size(); i++) {
            if (CARGO[i].equals(id) && rows.get(i).length > 0) return rows.get(i)[0];
        }
        return Messages.getName(g);
    }

    /**
     * A NAMES {@code @JOB} word: column 1 the work's («Holzfäller»),
     * column 2 the expert's plural («Erfahrene Holzfäller»).
     *
     * @param t The texts.
     * @param row The row.
     * @param col 0 or 1.
     * @return The word, or null.
     */
    static String jobWord(ClassicText t, int row, int col) {
        final List<String[]> rows = (t == null || row < 0) ? null : t.names("JOB");
        if (rows == null || row >= rows.size() || rows.get(row).length <= col) return null;
        final String s = rows.get(row)[col].trim();
        return (s.isEmpty()) ? null : s;
    }

    /**
     * The type's word in a title: its NAMES {@code @JOB} plural («Freie
     * Siedler», «Erfahrene Soldaten»), FreeCol's name for a type the
     * original lacks.
     *
     * @param t The texts.
     * @param u The unit.
     * @return The word.
     */
    static String typeWord(ClassicText t, Unit u) {
        final String w = jobWord(t, ClassicHud.jobRow(
            ClassicColonyBuildings.shortId(u.getType().getId())), 1);
        return (w != null) ? w : Messages.getName(u.getType());
    }

    /**
     * Whether a work tile is free for a colonist: nobody on it, not the
     * centre, ours or claimable (not water without docks, not a
     * neighbour's).
     *
     * @param ct The tile.
     * @return True if so.
     */
    static boolean free(ColonyTile ct) {
        if (ct == null || ct.isColonyCenterTile() || !ct.isEmpty()) return false;
        final NoAddReason r = ct.getNoWorkReason();
        return r == NoAddReason.NONE || r == NoAddReason.CLAIM_REQUIRED;
    }

    /**
     * The best free tile for a work (the «b» of the right column, and
     * where a pick of a tile work puts a colonist who is not on a tile, V
     * #4690, #20760, #97000): the most of the good for his type, the first
     * of the colony's tiles on a tie (I).
     *
     * @param c The colony.
     * @param ut His type.
     * @param g The good.
     * @return The tile, or null if none is free.
     */
    static ColonyTile bestFreeTile(Colony c, UnitType ut, GoodsType g) {
        ColonyTile best = null;
        int most = -1;
        for (ColonyTile ct : c.getColonyTiles()) {
            if (!free(ct)) continue;
            final int n = ct.getPotentialProduction(g, ut);
            if (n > most) {
                most = n;
                best = ct;
            }
        }
        return best;
    }

    /**
     * The colony's building for a work 9-18 (the teacher's: one that
     * teaches; FreeCol's chapel has no workplace, so it offers no
     * Prediger, dago-colony2 §3.2), or null where it has none.
     *
     * @param c The colony.
     * @param job The work.
     * @param g Its good, or null for the teacher.
     * @param ut His type.
     * @return The building, or null.
     */
    static Building buildingFor(Colony c, int job, GoodsType g, UnitType ut) {
        for (Building b : c.getBuildings()) {
            if (b.getUnitCapacity() <= 0) continue;
            if (job == ClassicColonyLayout.JOB_TEACHER) {
                if (b.canTeach()) return b;
            } else if (g != null && !b.canTeach() && b.canProduce(g, ut)) {
                return b;
            }
        }
        return null;
    }

    /**
     * The works the colony offers, in NAMES {@code @JOB} order 0-18 (the
     * tiles' nine always; a building's work where the colony has it:
     * Waffenschmied with the armory, Prediger with a church, Lehrer with a
     * school; dago-colony2 §3.2).
     *
     * @param c The colony.
     * @param ut The type asking.
     * @return The indices.
     */
    static List<Integer> works(Colony c, UnitType ut) {
        final Specification spec = c.getSpecification();
        final List<Integer> out = new ArrayList<>();
        for (int j = 0; j <= ClassicColonyLayout.JOB_TEACHER; j++) {
            if (j < ClassicColonyLayout.TILE_JOBS) {
                if (goodsOf(spec, j) != null) out.add(j);
            } else if (buildingFor(c, j, goodsOf(spec, j), ut) != null) {
                out.add(j);
            }
        }
        return out;
    }

    /**
     * A work's right cell for a unit (class comment).
     *
     * @param t The texts.
     * @param c The colony.
     * @param u The unit.
     * @param job The work.
     * @return The cell, or null (the teacher).
     */
    static String workCell(ClassicText t, Colony c, Unit u, int job) {
        if (job == ClassicColonyLayout.JOB_TEACHER) return null;
        final GoodsType g = goodsOf(c.getSpecification(), job);
        if (g == null) return null;
        final UnitType ut = u.getType();
        final String word = cargoName(t, g);
        if (job < ClassicColonyLayout.TILE_JOBS) {
            final ColonyTile best = bestFreeTile(c, ut, g);
            int b = (best == null) ? 0 : best.getPotentialProduction(g, ut);
            final ColonyTile own = (u.getLocation() instanceof ColonyTile
                && ((ColonyTile) u.getLocation()).getColony() == c)
                ? (ColonyTile) u.getLocation() : null;
            if (own == null) return b + " " + word;
            final int a = own.getPotentialProduction(g, ut);
            return a + "/" + Math.max(a, b) + " " + word;
        }
        final Building bd = buildingFor(c, job, g, ut);
        return ((bd == null) ? 0 : bd.getPotentialProduction(g, ut)) + " " + word;
    }

    /**
     * A role row for a unit: its words and cell (the amounts from FreeCol's
     * role, 50 in both rules, V), whether the warehouse can equip it (what
     * the unit lacks of it, against the warehouse), its count.
     *
     * @param t The texts.
     * @param c The colony.
     * @param u The unit.
     * @param job The role's NAMES {@code @JOB} index, 19-24.
     * @param yellow Whether it is the unit's own.
     * @return The row, or null where the role is not offered.
     */
    static Row roleRow(ClassicText t, Colony c, Unit u, int job, boolean yellow) {
        final Specification spec = c.getSpecification();
        final Role role = roleOf(spec, job);
        if (role == null) return null;
        if (job == ClassicColonyLayout.JOB_MISSIONARY
            && !c.hasAbility(Ability.DRESS_MISSIONARY)) return null;
        if (!role.isDefaultRole() && !u.roleIsAvailable(role)) return null;
        final String label = jobWord(t, job, 0);
        if (label == null) return null;
        int count = (role.isDefaultRole()) ? 0 : 1;
        if (job == ClassicColonyLayout.JOB_PIONEER) {
            final GoodsType tools = spec.getGoodsType("model.goods.tools");
            final int per = perCount(role, tools);
            final int n = pioneerTools(c.getGoodsCount(tools));
            count = Math.max(1, Math.min(role.getMaximumCount(), n / Math.max(1, per)));
        }
        final StringBuilder cell = new StringBuilder();
        for (AbstractGoods ag : role.getRequiredGoodsList(count)) {
            if (cell.length() > 0) cell.append(' ');
            cell.append('(').append(ag.getAmount()).append(' ')
                .append(cargoName(t, ag.getType())).append(')');
        }
        boolean grey = false;
        for (AbstractGoods ag : u.getGoodsDifference(role, count)) {
            if (ag.getAmount() > 0 && c.getGoodsCount(ag.getType()) < ag.getAmount()) grey = true;
        }
        return new Row(Pick.ROLE, job, label, (cell.length() == 0) ? null : cell.toString(),
                       yellow, grey, count);
    }

    /** @return A role's goods of a type per count (the pioneer's 20 tools). */
    private static int perCount(Role role, GoodsType g) {
        for (AbstractGoods ag : role.getRequiredGoodsList(1)) {
            if (ag.getType() == g) return ag.getAmount();
        }
        return 20;
    }

    /**
     * Whether a unit can drop his speciality: an expert FreeCol can turn
     * into a free colonist (the original's «LÖSCHEN» row only for an
     * expert, V #253).
     *
     * @param u The unit.
     * @return True if so.
     */
    static boolean canClear(Unit u) {
        return expertJob(u) >= 0
            && u.getUnitChange(net.sf.freecol.common.model.UnitChangeType.CLEAR_SKILL) != null;
    }

    /**
     * The title of a menu (class comment): «Beruf wählen für» + the type's
     * plural, with «(job)» for a colonist in the colony who does not work
     * in his speciality, and «:».
     *
     * @param t The texts.
     * @param u The unit.
     * @param kind The menu.
     * @return The title.
     */
    static String title(ClassicText t, Unit u, Kind kind) {
        final String choose = (t == null) ? null : t.label("CTITLE", CTITLE_CHOOSE);
        final StringBuilder sb = new StringBuilder((choose == null) ? "Beruf wählen für"
                                                   : choose.trim());
        sb.append(' ').append(typeWord(t, u));
        if (kind != Kind.OUTSIDE) {
            final int now = currentJob(u);
            if (now != expertJob(u)) {
                final String w = jobWord(t, now, 0);
                if (w != null) sb.append('(').append(w).append(')');
            }
        }
        return sb.append(':').toString();
    }

    /**
     * A job menu (class comment).
     *
     * @param t The original texts.
     * @param c The colony.
     * @param u The unit.
     * @param kind What menu.
     * @param page 1, or 2 for «(Weitere)» (J1: 1).
     * @return The menu, or null without the texts.
     */
    static Menu menu(ClassicText t, Colony c, Unit u, Kind kind, int page) {
        if (t == null || c == null || u == null) return null;
        final String more = t.label("CTITLE", CTITLE_MORE);
        if (more == null) return null;
        final int now = currentJob(u);
        final int expert = expertJob(u);
        final List<Row> rows = new ArrayList<>();
        if (kind == Kind.FENCE_DROP) {
            final String w = jobWord(t, now, 0);
            rows.add(new Row(Pick.STAY, now, (w == null) ? "?" : w, null, now == expert,
                             false, 0));
            for (int j = ClassicColonyLayout.JOB_COLONIST;
                 j <= ClassicColonyLayout.JOB_MISSIONARY; j++) {
                final Row r = roleRow(t, c, u, j, j == expert);
                if (r != null) rows.add(r);
            }
            return new Menu(kind, 1, title(t, u, kind), rows, 0);
        }
        final List<Integer> works = works(c, u.getType());
        final List<Integer> first = works.subList(0, Math.min(PAGE_JOBS, works.size()));
        final List<Integer> rest = works.subList(first.size(), works.size());
        if (page == 1) {
            final String clear = t.misc(MISC_CLEAR);
            if (canClear(u) && clear != null) {
                rows.add(new Row(Pick.CLEAR, -1, clear.trim(), null, false, false, 0));
            }
            for (int j : first) rows.add(workRow(t, c, u, j, expert));
            rows.add(new Row(Pick.MORE, -1, more.trim(), null, false, false, 0));
        } else {
            rows.add(new Row(Pick.MORE, -1, more.trim(), null, false, false, 0));
            for (int j : rest) rows.add(workRow(t, c, u, j, expert));
            for (int j = ClassicColonyLayout.JOB_COLONIST;
                 j <= ClassicColonyLayout.JOB_MISSIONARY; j++) {
                final Row r = roleRow(t, c, u, j, j == expert);
                if (r != null) rows.add(r);
            }
        }
        int bar = 0;
        for (int i = 0; i < rows.size(); i++) {
            final Row r = rows.get(i);
            if ((r.pick == Pick.JOB || r.pick == Pick.ROLE) && r.job == now) bar = i;
        }
        // A unit at the fence: page 1 opens on row 1 (V #96, #97, #253),
        // its «(Weitere)» page on its role (#254).
        if (kind == Kind.OUTSIDE && page == 1) bar = 0;
        return new Menu(kind, page, title(t, u, kind), rows, bar);
    }

    /** A work's row: its word and cell, yellow for the expert's. */
    private static Row workRow(ClassicText t, Colony c, Unit u, int job, int expert) {
        final String w = jobWord(t, job, 0);
        return new Row(Pick.JOB, job, (w == null) ? "?" : w, workCell(t, c, u, job),
                       job == expert, false, 0);
    }

    /**
     * The list box of a menu (the D2 extensions, golden on clip008 #42043
     * and #42539): the title flowed to 190, the rows at + 13, the cells
     * ending 13 px from the right, yellow and grey rows, the footer, the
     * bar; Escape and a click outside close it with no change (V: closed
     * with the bar unmoved, nothing changes, #161-#173; dago: a press
     * outside removes the bar).
     *
     * @param t The texts.
     * @param m The menu.
     * @param openDelayMs When it comes after it is asked (frames, §1.4).
     * @param chainMs The least time after the box before (the page), or
     *     negative for the usual.
     * @return The request, or null.
     */
    static ClassicAdvisorBox.Request request(ClassicText t, Menu m, double openDelayMs,
                                             double chainMs) {
        if (t == null || m == null) return null;
        final List<String> labels = new ArrayList<>();
        final List<String> cells = new ArrayList<>();
        final boolean[] yellow = new boolean[m.rows.size()];
        final boolean[] grey = new boolean[m.rows.size()];
        for (int i = 0; i < m.rows.size(); i++) {
            final Row r = m.rows.get(i);
            labels.add(ClassicAdvisorBox.literal(r.label));
            cells.add((r.cell == null) ? null : ClassicAdvisorBox.literal(r.cell));
            yellow[i] = r.yellow;
            grey[i] = r.grey;
        }
        return ClassicAdvisorBox.Request.builder("jobs " + m.kind + " " + m.page)
            .freeColText(m.title).width(WIDTH).rows(labels).right(cells)
            .rowIndent(ClassicMenuBox.LIST_INDENT).current(yellow).disabled(grey)
            .footer(t.misc(ClassicFathers.MISC_F1_HELP)).defaultRow(m.bar)
            .noCancelRow().openDelay(openDelayMs).chain(chainMs)
            .stopgap(m.title, null).list().build();
    }
}
