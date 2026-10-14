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

import java.util.Arrays;
import java.util.function.ToIntFunction;

import net.sf.freecol.common.option.OriginalWorldSettings;


/**
 * The original's <b>new-game chain</b> as a Swing-free state machine:
 * difficulty, European power, leader name, the chosen nation's two text
 * pages and the audience, in that order ({@code opening_056..068}); from
 * the title's «Neue Welt INDIVIDUALISIEREN» the world screen comes first
 * (its four questions, Roger's land tests {@code opening_024},
 * {@code 026..032}), from «Ein Spiel in der NEUEN WELT starten» not (the
 * original's small world, {@link OriginalWorldSettings#CLASSIC_UI_NEW_WORLD}).
 *
 * <p>The world screen as recorded: it opens with the middle row chosen in
 * every column and LANDMASSE current (yellow); a click on a picture chooses
 * it in its column and makes that column current (the column before turns
 * green); the pointer over a picture changes nothing; a click on the footer
 * goes on to the difficulty screen, which opens on its first card as from
 * the title.  Not recorded (Roger used the mouse only), so documented
 * assumptions: Left/Right move the current column, Up/Down its answer (all
 * clamped), Enter = the footer, Escape and a right click = back; a click
 * on a chosen picture only makes its column current.
 *
 * <p>Why separate from the panel: everything here -- stepping, clicks,
 * Escape-back, the name editor -- is decided without a window, so it is unit
 * tested ({@code ClassicNewWorldChainTest}) and the preview harness drives
 * exactly the operations a player would.  {@link ClassicMainMenuPanel} only
 * maps keys and mouse events onto these operations and repaints according to
 * the {@link Result}.  EDT only.
 *
 * <p>What the captures prove: the highlight steps in reading order and
 * starts at index 0 on both pickers (GAME.TXT {@code @PICKNATION}'s
 * {@code @default=1} is 1-based); the arrow stays parked, so the original
 * was driven by keyboard.  Everything else here is a documented assumption
 * (see the README's input model): clamped stepping without wrap like the
 * title menu, click-to-select and click-again-to-confirm, the footer as a
 * confirm button, Escape or a right click = back, any key or click advances
 * the text pages and the audience, typing replaces the highlighted default
 * name.
 */
final class ClassicNewWorldChain {

    /** The screens, in order (WORLD only from INDIVIDUALISIEREN). */
    enum Step { WORLD, DIFFICULTY, NATION, NAME, PAGE_A, PAGE_B, AUDIENCE }

    /** What an operation means for the host. */
    enum Result {
        /** Nothing changed. */
        STAY,
        /** The view changed; repaint. */
        REPAINT,
        /** Left the chain backwards from the first screen: back to the title. */
        CANCEL,
        /** The audience was dismissed: start the game with {@link #setup}. */
        DONE
    }

    /** An immutable snapshot for the painters. */
    static final class View {

        final Step step;
        final int diffSel, nationSel;
        final String name;
        final boolean nameSelected;
        /** The world screen: the chosen row of each column (a copy). */
        final int[] worldRows;
        /** The world screen: the current column. */
        final int worldColumn;

        View(Step step, int diffSel, int nationSel, String name, boolean nameSelected) {
            this(step, diffSel, nationSel, name, nameSelected, DEFAULT_WORLD_ROWS, 0);
        }

        View(Step step, int diffSel, int nationSel, String name, boolean nameSelected,
             int[] worldRows, int worldColumn) {
            this.step = step;
            this.diffSel = diffSel;
            this.nationSel = nationSel;
            this.name = name;
            this.nameSelected = nameSelected;
            this.worldRows = worldRows.clone();
            this.worldColumn = worldColumn;
        }
    }

    /**
     * The world screen's answers when it opens: the middle row in every
     * column (every recording, also after a game with other answers:
     * {@code 027} after {@code 024}).
     */
    static final int[] DEFAULT_WORLD_ROWS = {
        OriginalWorldSettings.MIDDLE, OriginalWorldSettings.MIDDLE,
        OriginalWorldSettings.MIDDLE, OriginalWorldSettings.MIDDLE };

    /**
     * Widest name in pixels: the box (79,98,167,14) of {@code opening_065}
     * has its text at x=82, and text plus caret must end by x=245, the box's
     * right edge -- 245 - 82 = 163.
     */
    static final int NAME_MAX_WIDTH = 163;

    /**
     * Fallback name length: the GAME.TXT {@code @LEADERNAME} input field is
     * 22 underscores ({@link ClassicNewWorldScreens.Texts#nameFieldLength}).
     */
    static final int DEFAULT_NAME_LENGTH = 22;

    /** Characters the name never takes: the original's text markup. */
    private static final String MARKUP = "{}^_~%@";


    /** The nations' default leaders, in original order. */
    private final String[] defaultLeaders;

    /** Pixel width of a name as drawn (FONTINTR). */
    private final ToIntFunction<String> width;

    /** Most characters in a name. */
    private final int maxLength;

    /** Whether the chain began on the world screen (INDIVIDUALISIEREN). */
    private final boolean world;

    private Step step;
    private int diffSel = 0;
    private int nationSel = 0;

    /** The world screen's chosen rows and current column. */
    private final int[] worldRows = DEFAULT_WORLD_ROWS.clone();
    private int worldColumn = 0;
    private final StringBuilder name = new StringBuilder();

    /** The name is the untouched, highlighted default. */
    private boolean nameSelected = true;

    /** The player changed the name since the last forward entry. */
    private boolean nameEdited = false;

    /** The confirmed name. */
    private String finalName = null;


    /**
     * Create a chain at the difficulty screen, both selections 0.
     *
     * @param defaultLeaders The four default leader names (NAMES.TXT
     *     {@code @LEADERNAME}), England, France, Spain, Holland.
     * @param width The drawn width of a name.
     * @param maxLength The longest name in characters.
     */
    ClassicNewWorldChain(String[] defaultLeaders, ToIntFunction<String> width,
                         int maxLength) {
        this(defaultLeaders, width, maxLength, false);
    }

    /**
     * Create a chain.
     *
     * @param defaultLeaders The four default leader names.
     * @param width The drawn width of a name.
     * @param maxLength The longest name in characters.
     * @param world True to begin on the world screen (INDIVIDUALISIEREN),
     *     false on the difficulty screen (NEUE WELT).
     */
    ClassicNewWorldChain(String[] defaultLeaders, ToIntFunction<String> width,
                         int maxLength, boolean world) {
        this.defaultLeaders = defaultLeaders.clone();
        this.width = width;
        this.maxLength = (maxLength > 0) ? maxLength : DEFAULT_NAME_LENGTH;
        this.world = world;
        this.step = world ? Step.WORLD : Step.DIFFICULTY;
    }

    /** A chain over loaded texts, measuring names in {@code nameFont}. */
    static ClassicNewWorldChain create(ClassicNewWorldScreens.Texts t,
                                       final ClassicFont nameFont) {
        return create(t, nameFont, false);
    }

    /** A chain over loaded texts, beginning on the world screen or not. */
    static ClassicNewWorldChain create(ClassicNewWorldScreens.Texts t,
                                       final ClassicFont nameFont, boolean world) {
        return new ClassicNewWorldChain(t.defaultLeaders, nameFont::stringWidth,
                                        t.nameFieldLength, world);
    }

    /** @return A snapshot of the current state. */
    View view() {
        return new View(this.step, this.diffSel, this.nationSel,
                        this.name.toString(), this.nameSelected,
                        this.worldRows, this.worldColumn);
    }

    /** @return The current screen. */
    Step step() {
        return this.step;
    }

    /** @return Whether the chain began on the world screen. */
    boolean beganWithWorld() {
        return this.world;
    }

    /**
     * The choices, after {@link Result#DONE}.
     *
     * @return The setup for {@code ClassicGUI.beginNewWorldSetup}: the
     *     world screen's answers, or -- from NEUE WELT -- the original's
     *     small world ({@link OriginalWorldSettings#CLASSIC_UI_NEW_WORLD}).
     */
    ClassicGUI.NewWorldSetup setup() {
        return new ClassicGUI.NewWorldSetup(
            ClassicNewWorldScreens.DIFFICULTY_IDS[this.diffSel],
            ClassicNewWorldScreens.NATION_IDS[this.nationSel],
            (this.finalName != null) ? this.finalName : defaultLeader(),
            worldSettings());
    }

    /**
     * The world of this chain.
     *
     * @return The world screen's answers (INDIVIDUALISIEREN), else
     *     {@link OriginalWorldSettings#CLASSIC_UI_NEW_WORLD}.
     */
    OriginalWorldSettings worldSettings() {
        return (this.world)
            ? new OriginalWorldSettings(this.worldRows[0], this.worldRows[1],
                                        this.worldRows[2], this.worldRows[3])
            : OriginalWorldSettings.CLASSIC_UI_NEW_WORLD;
    }


    // The world screen

    /**
     * Choose a picture: its row in its column, and its column current.
     *
     * @param column The column (0 LANDMASSE .. 3 KLIMA).
     * @param row The row (0 top .. 2 bottom).
     * @return REPAINT if anything changed.
     */
    Result chooseWorld(int column, int row) {
        if (this.step != Step.WORLD) return Result.STAY;
        final int c = clamp(column, this.worldRows.length);
        final int r = clamp(row, ClassicNewWorldScreens.WORLD_Y.length);
        if (c == this.worldColumn && r == this.worldRows[c]) return Result.STAY;
        this.worldColumn = c;
        this.worldRows[c] = r;
        return Result.REPAINT;
    }

    /** Left/Right on the world screen: the current column (clamped). */
    Result moveWorldColumn(int delta) {
        if (this.step != Step.WORLD) return Result.STAY;
        final int c = clamp(this.worldColumn + delta, this.worldRows.length);
        return chooseWorld(c, this.worldRows[c]);
    }

    /** Up/Down on the world screen: the current column's answer (clamped). */
    Result moveWorldRow(int delta) {
        if (this.step != Step.WORLD) return Result.STAY;
        return chooseWorld(this.worldColumn, this.worldRows[this.worldColumn] + delta);
    }

    /** @return The world screen's chosen rows (a copy). */
    int[] worldRows() {
        return Arrays.copyOf(this.worldRows, this.worldRows.length);
    }


    // Pickers

    /** The highlight one card back in reading order (clamped). */
    Result prev() {
        return select(current() - 1);
    }

    /** The highlight one card on in reading order (clamped). */
    Result next() {
        return select(current() + 1);
    }

    /** The first card. */
    Result first() {
        return select(0);
    }

    /** The last card. */
    Result last() {
        return select(Integer.MAX_VALUE);
    }

    private int current() {
        return (this.step == Step.NATION) ? this.nationSel : this.diffSel;
    }

    private Result select(int i) {
        final int n;
        if (this.step == Step.DIFFICULTY) {
            n = clamp(i, ClassicNewWorldScreens.DIFFICULTY_IDS.length);
            if (n == this.diffSel) return Result.STAY;
            this.diffSel = n;
        } else if (this.step == Step.NATION) {
            n = clamp(i, ClassicNewWorldScreens.NATION_IDS.length);
            if (n == this.nationSel) return Result.STAY;
            this.nationSel = n;
        } else {
            return Result.STAY;
        }
        return Result.REPAINT;
    }

    private static int clamp(int i, int count) {
        return Math.max(0, Math.min(count - 1, i));
    }


    // Forward and back

    /**
     * Enter (or a confirming click): on to the next screen; from the
     * audience, {@link Result#DONE}.
     */
    Result confirm() {
        switch (this.step) {
        case WORLD:
            this.step = Step.DIFFICULTY;
            this.diffSel = 0;           // forward entry: the first card (024 #1072)
            return Result.REPAINT;
        case DIFFICULTY:
            this.step = Step.NATION;
            this.nationSel = 0;          // forward entry: England, as 035/061
            return Result.REPAINT;
        case NATION:
            this.step = Step.NAME;
            this.name.setLength(0);
            this.name.append(defaultLeader());
            this.nameSelected = true;
            this.nameEdited = false;
            return Result.REPAINT;
        case NAME:
            final String n = this.name.toString().trim();
            this.finalName = n.isEmpty() ? defaultLeader() : n;
            if (n.isEmpty()) {
                this.name.setLength(0);
                this.name.append(this.finalName);
            }
            this.step = Step.PAGE_A;
            return Result.REPAINT;
        case PAGE_A:
            this.step = Step.PAGE_B;
            return Result.REPAINT;
        case PAGE_B:
            this.step = Step.AUDIENCE;
            return Result.REPAINT;
        case AUDIENCE: default:
            return Result.DONE;
        }
    }

    /**
     * Escape (or a right click): one screen back, keeping the choices made
     * there; from the first screen (the world screen, else the difficulty
     * screen), {@link Result#CANCEL}.
     */
    Result back() {
        switch (this.step) {
        case WORLD:
            return Result.CANCEL;
        case DIFFICULTY:
            if (this.world) {
                this.step = Step.WORLD;
                return Result.REPAINT;
            }
            return Result.CANCEL;
        case NATION:
            this.step = Step.DIFFICULTY;
            return Result.REPAINT;
        case NAME:
            this.step = Step.NATION;
            return Result.REPAINT;
        case PAGE_A:
            this.step = Step.NAME;
            this.nameSelected = !this.nameEdited;
            return Result.REPAINT;
        case PAGE_B:
            this.step = Step.PAGE_A;
            return Result.REPAINT;
        case AUDIENCE: default:
            this.step = Step.PAGE_B;
            return Result.REPAINT;
        }
    }

    /**
     * Any key except Escape on a text page or the audience: advance.
     * Nothing elsewhere.
     */
    Result anyKey() {
        return advancesOnAnyInput() ? confirm() : Result.STAY;
    }

    /** Whether the current screen is a "press any key" screen. */
    boolean advancesOnAnyInput() {
        return this.step == Step.PAGE_A || this.step == Step.PAGE_B
            || this.step == Step.AUDIENCE;
    }


    // Mouse

    /**
     * A left click at a virtual point.  World screen: a picture is chosen
     * in its column, the footer goes on.  Pickers: a card selects it, the
     * highlighted card again or the footer confirms.  Name: a click in the
     * box drops the default's highlight.  Pages and audience: advance.
     */
    Result clickPrimary(int x, int y) {
        switch (this.step) {
        case WORLD:
            final int[] cell = ClassicNewWorldScreens.worldCellAt(x, y);
            if (cell != null) return chooseWorld(cell[0], cell[1]);
            return ClassicNewWorldScreens.footerHit(this.step, x, y)
                ? confirm() : Result.STAY;
        case DIFFICULTY: case NATION:
            final int card = ClassicNewWorldScreens.cardAt(this.step, x, y);
            if (card >= 0) {
                return (card == current()) ? confirm() : select(card);
            }
            return ClassicNewWorldScreens.footerHit(this.step, x, y)
                ? confirm() : Result.STAY;
        case NAME:
            if (ClassicNewWorldScreens.NAME_BOX.contains(x, y) && this.nameSelected) {
                this.nameSelected = false;
                return Result.REPAINT;
            }
            return Result.STAY;
        default:
            return confirm();
        }
    }

    /** A right click: back, like Escape. */
    Result clickSecondary(int x, int y) {
        return back();
    }


    // Name editor

    /**
     * A typed character on the name screen.  The first one replaces the
     * highlighted default; markup and characters the game cannot draw are
     * ignored; the name stays within the field's length and the box.
     */
    Result typed(char c) {
        if (this.step != Step.NAME || !typeable(c)) return Result.STAY;
        if (this.width.applyAsInt(String.valueOf(c)) <= 0) return Result.STAY;
        final String cand = (this.nameSelected ? "" : this.name.toString()) + c;
        if (cand.length() > this.maxLength
            || this.width.applyAsInt(cand) + this.width.applyAsInt("_")
               > NAME_MAX_WIDTH) {
            return Result.STAY;
        }
        this.name.setLength(0);
        this.name.append(cand);
        this.nameSelected = false;
        this.nameEdited = true;
        return Result.REPAINT;
    }

    /**
     * Backspace on the name screen: clears the highlighted default, else
     * deletes the last character.
     */
    Result backspace() {
        if (this.step != Step.NAME) return Result.STAY;
        if (this.nameSelected) {
            this.name.setLength(0);
            this.nameSelected = false;
        } else if (this.name.length() > 0) {
            this.name.setLength(this.name.length() - 1);
        } else {
            return Result.STAY;
        }
        this.nameEdited = true;
        return Result.REPAINT;
    }

    /**
     * Whether a character may enter a name: printable, not the original's
     * markup, and one the game's character set has (ä ö ü Ä Ö Ü ß and
     * printable ASCII; accented letters lose their accent when drawn, as
     * {@link ClassicFont#toCode} folds them).
     */
    static boolean typeable(char c) {
        if (c < 32 || c == 127 || MARKUP.indexOf(c) >= 0) return false;
        if (c == '\\' || c == '`' || c == '|') return false;   // draw as Ü ä ö
        return c == '?' || ClassicFont.toCode(c) != '?';
    }

    /** @return The default leader of the selected nation. */
    private String defaultLeader() {
        return (this.nationSel < this.defaultLeaders.length)
            ? this.defaultLeaders[this.nationSel] : "";
    }
}
