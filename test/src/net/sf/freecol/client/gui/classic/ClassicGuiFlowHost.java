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

import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * A turn flow's host for the GUI tests of part W's final fixer (the review
 * of part W, regression L2, L3, L5): the flow asks the test's
 * {@link ClassicGUI} whether it holds ({@link ClassicGUI#turnBlocked}), so
 * the GUI's holds (a colony screen, the colony reports, a docking) and a
 * screen's close ({@link ClassicTurnFlow#screenClosed}) act on a real
 * {@link ClassicTurnFlow}; an activation is recorded and tells the GUI
 * ({@link ClassicGUI#unitUp}), as the game's host does.  Not a test itself.
 */
final class ClassicGuiFlowHost implements ClassicTurnFlow.Host {

    final ClassicGUI gui;
    final Player me;
    final ClassicSlide.Clock clock;

    /** Whether it is our turn. */
    boolean myTurn = true;

    /** The map's active unit. */
    Unit active = null;

    /** The calls: "activate <id>", "wipe", "endTurn". */
    final List<String> calls = new ArrayList<>();

    /** What the flow posts, run by {@link #runPosted}. */
    final List<Runnable> posted = new ArrayList<>();

    ClassicGuiFlowHost(ClassicGUI gui, Player me, ClassicSlide.Clock clock) {
        this.gui = gui;
        this.me = me;
        this.clock = clock;
    }

    /** Run what the flow posted (EDT in the tests). */
    void runPosted() {
        for (int guard = 0; guard < 100 && !this.posted.isEmpty(); guard++) {
            final List<Runnable> now = new ArrayList<>(this.posted);
            this.posted.clear();
            for (Runnable r : now) r.run();
        }
    }

    /** @return How often {@code call} came. */
    int count(String call) {
        int n = 0;
        for (String c : this.calls) if (c.equals(call)) n++;
        return n;
    }

    @Override public boolean myTurn() { return this.myTurn; }
    @Override public boolean blocked() { return this.gui.turnBlocked(); }
    @Override public boolean hasNextActiveUnit() { return false; }
    /** The real cycle's kind (no visits, no goto run): a cycle's hand-over brings a due unit only. */
    private final ClassicUnitCycle cycle = new ClassicUnitCycle();

    @Override public ClassicUnitCycle.Kind dueKind(Unit unit) { return this.cycle.kind(unit); }
    @Override public Unit cycleNext(Unit anchor) { return null; }
    @Override public void finished(Unit unit) { }
    @Override public boolean anyDue() { return false; }
    @Override public void putBack(Unit unit) { }
    @Override public int turnNumber() { return 1; }
    @Override public boolean promptPref() { return false; }
    @Override public Unit activeUnit() { return this.active; }
    @Override public boolean wouldJump(Unit unit) { return false; }
    @Override public void jumpTo(Unit unit) { this.calls.add("jump " + unit.getId()); }

    @Override
    public void activate(Unit unit) {
        this.calls.add("activate " + unit.getId());
        this.active = unit;
        this.gui.unitUp(unit, this.clock.now());
    }

    @Override public void wipe() { this.calls.add("wipe"); }
    @Override public void paintIndicator() { }

    @Override
    public boolean endTurn() {
        this.calls.add("endTurn");
        return false;
    }

    @Override public void runGoto(Unit unit) { this.calls.add("goto " + unit.getId()); }
    @Override public void visit(Unit unit) { this.calls.add("visit " + unit.getId()); }
    @Override public void visitShown(Unit unit) { }
    @Override public void nextActiveUnit() { }
    @Override public void enterPrompt(Tile village) { this.calls.add("prompt"); }
    @Override public void freezePrompt() { }
    @Override public void leavePrompt() { }
    @Override public Player currentPlayer() { return (this.myTurn) ? this.me : null; }
    @Override public Player myPlayer() { return this.me; }
    @Override public Player nextPlayer() { return null; }
    @Override public void post(Runnable r) { this.posted.add(r); }
}
