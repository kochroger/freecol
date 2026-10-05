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

import junit.framework.TestCase;

import net.sf.freecol.client.gui.classic.ClassicNewWorldChain.Result;
import net.sf.freecol.client.gui.classic.ClassicNewWorldChain.Step;


/**
 * Tests of the new-game chain's state machine ({@link ClassicNewWorldChain})
 * with synthetic leader names and a fixed-width "font".
 */
public class ClassicNewWorldChainTest extends TestCase {

    private static final String[] LEADERS = { "Ann", "Bob", "Cy", "Dan Dee" };

    /** 7 px per character (so '_' is 7 like FONTINTR's), 22 characters. */
    private static ClassicNewWorldChain chain() {
        return new ClassicNewWorldChain(LEADERS, s -> 7 * s.length(), 22);
    }

    /** Click the centre of a card. */
    private static Result click(ClassicNewWorldChain c, int[] card) {
        return c.clickPrimary(card[0] + card[2] / 2, card[1] + card[3] / 2);
    }

    private static ClassicNewWorldChain atName(int nation) {
        final ClassicNewWorldChain c = chain();
        c.confirm();
        for (int i = 0; i < nation; i++) c.next();
        c.confirm();
        assertEquals(Step.NAME, c.step());
        return c;
    }

    public void testInitialState() {
        final ClassicNewWorldChain.View v = chain().view();
        assertEquals(Step.DIFFICULTY, v.step);
        assertEquals(0, v.diffSel);
        assertEquals(0, v.nationSel);
    }

    public void testSteppingIsClamped() {
        final ClassicNewWorldChain c = chain();
        assertEquals(Result.STAY, c.prev());
        assertEquals(Result.REPAINT, c.next());
        for (int i = 0; i < 10; i++) c.next();
        assertEquals(4, c.view().diffSel);
        assertEquals(Result.STAY, c.next());
        assertEquals(Result.REPAINT, c.first());
        assertEquals(0, c.view().diffSel);
        c.last();
        assertEquals(4, c.view().diffSel);
        c.confirm();
        assertEquals(Step.NATION, c.step());
        assertEquals(0, c.view().nationSel);
        for (int i = 0; i < 10; i++) c.next();
        assertEquals(3, c.view().nationSel);
    }

    public void testClicksSelectThenConfirm() {
        final ClassicNewWorldChain c = chain();
        assertEquals(Result.REPAINT, click(c, ClassicNewWorldScreens.DIFF_CARDS[2]));
        assertEquals(2, c.view().diffSel);
        assertEquals(Step.DIFFICULTY, c.step());
        assertEquals(Result.REPAINT, click(c, ClassicNewWorldScreens.DIFF_CARDS[2]));
        assertEquals(Step.NATION, c.step());
        // Outside any card: nothing.
        assertEquals(Result.STAY, c.clickPrimary(5, 5));
        click(c, ClassicNewWorldScreens.NATION_CARDS[3]);
        assertEquals(3, c.view().nationSel);
        // The footer confirms.
        assertEquals(Result.REPAINT, c.clickPrimary(10, 183));
        assertEquals(Step.NAME, c.step());
        assertEquals("Dan Dee", c.view().name);
    }

    public void testDifficultyFooterConfirms() {
        final ClassicNewWorldChain c = chain();
        c.clickPrimary(50, 82);
        assertEquals(Step.NATION, c.step());
    }

    public void testBackChain() {
        final ClassicNewWorldChain c = chain();
        c.next();
        c.confirm();                     // NATION
        c.next();
        c.confirm();                     // NAME
        c.confirm();                     // PAGE_A
        c.confirm();                     // PAGE_B
        c.confirm();                     // AUDIENCE
        assertEquals(Step.AUDIENCE, c.step());
        assertEquals(Result.REPAINT, c.back());
        assertEquals(Step.PAGE_B, c.step());
        c.clickSecondary(0, 0);          // right click = back
        assertEquals(Step.PAGE_A, c.step());
        c.back();
        assertEquals(Step.NAME, c.step());
        c.back();
        assertEquals(Step.NATION, c.step());
        assertEquals(1, c.view().nationSel);     // kept on a back return
        c.back();
        assertEquals(Step.DIFFICULTY, c.step());
        assertEquals(1, c.view().diffSel);
        assertEquals(Result.CANCEL, c.back());
    }

    public void testPagesAdvanceOnAnyInput() {
        final ClassicNewWorldChain c = atName(3);
        assertEquals(Result.STAY, c.anyKey());   // not on the name screen
        c.confirm();
        assertTrue(c.advancesOnAnyInput());
        assertEquals(Result.REPAINT, c.anyKey());
        assertEquals(Step.PAGE_B, c.step());
        assertEquals(Result.REPAINT, c.clickPrimary(1, 1));
        assertEquals(Step.AUDIENCE, c.step());
        assertEquals(Result.DONE, c.anyKey());
    }

    public void testNameEditing() {
        final ClassicNewWorldChain c = atName(0);
        assertEquals("Ann", c.view().name);
        assertTrue(c.view().nameSelected);
        // The first character replaces the highlighted default.
        assertEquals(Result.REPAINT, c.typed('Z'));
        assertEquals("Z", c.view().name);
        assertFalse(c.view().nameSelected);
        c.typed('ö');
        assertEquals("Zö", c.view().name);
        c.backspace();
        assertEquals("Z", c.view().name);
        // Markup and undrawable characters are ignored.
        for (char m : new char[] { '{', '}', '^', '_', '~', '%', '@', '\n', '€' }) {
            assertEquals(Result.STAY, c.typed(m));
        }
        assertEquals("Z", c.view().name);
    }

    public void testBackspaceClearsSelectedDefault() {
        final ClassicNewWorldChain c = atName(1);
        assertEquals(Result.REPAINT, c.backspace());
        assertEquals("", c.view().name);
        assertFalse(c.view().nameSelected);
        assertEquals(Result.STAY, c.backspace());
    }

    public void testClickInBoxDropsHighlight() {
        final ClassicNewWorldChain c = atName(2);
        assertEquals(Result.REPAINT, c.clickPrimary(100, 104));
        assertFalse(c.view().nameSelected);
        assertEquals("Cy", c.view().name);
        c.typed('x');
        assertEquals("Cyx", c.view().name);
    }

    public void testLengthLimit() {
        final ClassicNewWorldChain c = new ClassicNewWorldChain(LEADERS, s -> s.length(), 22);
        c.confirm();
        c.confirm();
        for (int i = 0; i < 30; i++) c.typed('a');
        assertEquals(22, c.view().name.length());
    }

    public void testWidthLimit() {
        // 10 px per character: 10n + 10 ('_') <= 163 -> 15 characters.
        final ClassicNewWorldChain c = new ClassicNewWorldChain(LEADERS, s -> 10 * s.length(), 22);
        c.confirm();
        c.confirm();
        for (int i = 0; i < 30; i++) c.typed('a');
        assertEquals(15, c.view().name.length());
    }

    public void testEmptyNameUsesDefault() {
        final ClassicNewWorldChain c = atName(3);
        c.backspace();
        c.typed(' ');
        assertEquals(Result.REPAINT, c.confirm());
        assertEquals(Step.PAGE_A, c.step());
        assertEquals("Dan Dee", c.setup().playerName);
    }

    public void testReenteringNameResetsDefault() {
        final ClassicNewWorldChain c = atName(0);
        c.typed('Q');
        c.back();                        // NATION
        c.next();
        c.confirm();                     // NAME again, forward
        assertEquals("Bob", c.view().name);
        assertTrue(c.view().nameSelected);
        // From the pages the typed name is kept.
        c.typed('L');
        c.typed('e');
        c.confirm();
        c.back();
        assertEquals("Le", c.view().name);
        assertFalse(c.view().nameSelected);
    }

    public void testSetupMapping() {
        final ClassicNewWorldChain c = atName(3);
        c.typed('L');
        c.typed('e');
        c.typed('v');
        c.typed('i');
        c.confirm();
        final ClassicGUI.NewWorldSetup s = c.setup();
        assertEquals("model.nation.dutch", s.nationId);
        assertEquals("model.difficulty.veryEasy", s.difficultyId);
        assertEquals("Levi", s.playerName);

        final ClassicNewWorldChain h = chain();
        h.last();
        h.confirm();
        h.confirm();
        h.confirm();
        final ClassicGUI.NewWorldSetup e = h.setup();
        assertEquals("model.difficulty.veryHard", e.difficultyId);
        assertEquals("model.nation.english", e.nationId);
        assertEquals("Ann", e.playerName);
    }
}
