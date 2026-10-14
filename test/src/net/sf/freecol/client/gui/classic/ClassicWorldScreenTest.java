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
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import net.sf.freecol.client.gui.classic.ClassicNewWorldChain.Result;
import net.sf.freecol.client.gui.classic.ClassicNewWorldChain.Step;
import net.sf.freecol.common.option.OriginalWorldSettings;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * «Neue Welt INDIVIDUALISIEREN» (part P3): the world screen of the
 * original as Roger recorded it in his land tests (2026-10-13,
 * {@code video/recordings/landtests/2026-10-13_opening_024} and
 * {@code 026..032}, decoded next to them): it opens with the middle row
 * chosen and LANDMASSE current, a click chooses a picture in its column
 * and makes the column current, the footer goes on to the difficulty
 * screen at its first card, and the answers become the new game's world;
 * the title's «Neue Welt» plays the original's small world.  The golden
 * check (the pack and {@code -Dclassic.clips}) paints the screen over
 * twelve of the clips' frames with 0 differing pixels.
 */
public class ClassicWorldScreenTest extends FreeColTestCase {

    private static final String[] LEADERS = { "Ann", "Bob", "Cy", "Dan Dee" };

    private static ClassicNewWorldChain worldChain() {
        return new ClassicNewWorldChain(LEADERS, s -> 7 * s.length(), 22, true);
    }

    /** {rows..., current column} of a chain. */
    private static String state(ClassicNewWorldChain c) {
        final ClassicNewWorldChain.View v = c.view();
        return v.worldRows[0] + "" + v.worldRows[1] + v.worldRows[2] + v.worldRows[3]
            + "/" + v.worldColumn;
    }

    /**
     * Roger's clicks on the world screen, per clip: the arrow where a
     * frame changed (x, y, then the state it shows: rows of the four
     * columns / the yellow column), the last one the footer click that
     * left the screen; and the game's world as his save recorded it
     * (prep2/W-landtests.md).
     */
    private static final Object[][] CLIPS = {
        { "024", new int[][] { { 47, 156 }, { 123, 157 }, { 199, 156 }, { 261, 155 },
                               { 194, 192 } },
          new String[] { "2111/0", "2211/1", "2221/2", "2222/3" }, "GCwf" },
        { "026", new int[][] { { 148, 191 } }, new String[0], "NNgn" },
        { "027", new int[][] { { 34, 39 }, { 197, 193 } }, new String[] { "0111/0" }, "KNgn" },
        { "028", new int[][] { { 51, 152 }, { 166, 193 } }, new String[] { "2111/0" }, "GNgn" },
        { "029", new int[][] { { 133, 142 }, { 153, 190 } }, new String[] { "1211/1" }, "NCgn" },
        { "030", new int[][] { { 116, 37 }, { 181, 190 } }, new String[] { "1011/1" }, "NIgn" },
        { "031", new int[][] { { 48, 47 }, { 176, 193 } }, new String[] { "0111/0" }, "KNgn" },
        { "032", new int[][] { { 52, 162 }, { 166, 192 } }, new String[] { "2111/0" }, "GNgn" },
    };

    /** Through the rest of the chain to its end (Holland, the default name). */
    private static void finish(ClassicNewWorldChain c) {
        assertEquals(Step.DIFFICULTY, c.step());
        c.confirm();                     // NATION
        for (int i = 0; i < 3; i++) c.next();
        c.confirm();                     // NAME
        c.confirm();                     // PAGE_A
        c.confirm();                     // PAGE_B
        c.confirm();                     // AUDIENCE
        assertEquals(Result.DONE, c.confirm());
    }

    public void testTheScreenOpensAsRecorded() {
        final ClassicNewWorldChain c = worldChain();
        assertEquals(Step.WORLD, c.step());
        assertTrue(c.beganWithWorld());
        assertEquals("1111/0", state(c));
        // NEUE WELT has no world screen.
        final ClassicNewWorldChain n = new ClassicNewWorldChain(LEADERS, s -> 7 * s.length(), 22);
        assertEquals(Step.DIFFICULTY, n.step());
        assertFalse(n.beganWithWorld());
    }

    /** Every recorded click gives the recorded screen and the recorded world. */
    public void testRogersClicks() {
        for (Object[] clip : CLIPS) {
            final String name = (String) clip[0];
            final int[][] clicks = (int[][]) clip[1];
            final String[] states = (String[]) clip[2];
            final ClassicNewWorldChain c = worldChain();
            for (int i = 0; i < clicks.length; i++) {
                final Result r = c.clickPrimary(clicks[i][0], clicks[i][1]);
                assertEquals(name + " click " + i, Result.REPAINT, r);
                if (i < states.length) {
                    assertEquals(name + " click " + i, states[i], state(c));
                    assertEquals(Step.WORLD, c.step());
                } else {
                    // The footer: the difficulty screen at its first card.
                    assertEquals(name, Step.DIFFICULTY, c.step());
                    assertEquals(name, 0, c.view().diffSel);
                }
            }
            finish(c);
            assertEquals(name, (String) clip[3], c.setup().world.getCode());
        }
    }

    /**
     * Between the pictures, on the heading, beside the footer and on the
     * current answer nothing changes; a chosen answer of another column
     * makes that column current.
     */
    public void testGapsAndTheChosenChangeNothing() {
        final ClassicNewWorldChain c = worldChain();
        for (int[] p : new int[][] { { 84, 100 }, { 5, 100 }, { 50, 70 }, { 50, 130 },
                                     { 160, 8 }, { 100, 192 }, { 220, 192 },
                                     { 50, 186 }, { 315, 100 }, { 50, 100 } }) {
            assertEquals(p[0] + "," + p[1], Result.STAY, c.clickPrimary(p[0], p[1]));
        }
        assertEquals("1111/0", state(c));
        assertEquals(Result.REPAINT, c.clickPrimary(200, 100));   // TEMPERATUR's Gemäßigt
        assertEquals("1111/2", state(c));
        assertEquals(Step.WORLD, c.step());
    }

    /** Escape and the right click go back; the choices are kept. */
    public void testBackAndCancel() {
        final ClassicNewWorldChain c = worldChain();
        c.clickPrimary(47, 156);
        c.confirm();
        assertEquals(Step.DIFFICULTY, c.step());
        c.next();
        assertEquals(Result.REPAINT, c.back());
        assertEquals(Step.WORLD, c.step());
        assertEquals("2111/0", state(c));
        c.confirm();
        assertEquals("forward entry: the first card", 0, c.view().diffSel);
        c.clickSecondary(0, 0);
        assertEquals(Step.WORLD, c.step());
        assertEquals(Result.CANCEL, c.back());
        // NEUE WELT: back from the difficulty screen is the title.
        final ClassicNewWorldChain n = new ClassicNewWorldChain(LEADERS, s -> 7 * s.length(), 22);
        assertEquals(Result.CANCEL, n.back());
    }

    /** The keys (an assumption, see the chain's Javadoc): clamped. */
    public void testTheKeys() {
        final ClassicNewWorldChain c = worldChain();
        assertEquals(Result.STAY, c.moveWorldColumn(-1));
        assertEquals(Result.REPAINT, c.moveWorldRow(1));
        assertEquals("2111/0", state(c));
        assertEquals(Result.STAY, c.moveWorldRow(1));
        assertEquals(Result.REPAINT, c.moveWorldColumn(1));
        assertEquals("2111/1", state(c));
        c.moveWorldRow(-1);
        c.moveWorldRow(-1);
        assertEquals("2011/1", state(c));
        for (int i = 0; i < 5; i++) c.moveWorldColumn(1);
        assertEquals("2011/3", state(c));
        // The pickers' steps do nothing here, and the moves nothing elsewhere.
        assertEquals(Result.STAY, c.next());
        c.confirm();
        assertEquals(Result.STAY, c.moveWorldRow(1));
        assertEquals(Result.STAY, c.chooseWorld(0, 0));
    }

    /**
     * The worlds: INDIVIDUALISIEREN untouched is everything Normal, the
     * title's «Neue Welt» the original's small world.
     */
    public void testTheWorlds() {
        final ClassicNewWorldChain c = worldChain();
        c.clickPrimary(148, 191);
        finish(c);
        assertEquals(OriginalWorldSettings.ALL_NORMAL, c.setup().world);
        final ClassicNewWorldChain n = new ClassicNewWorldChain(LEADERS, s -> 7 * s.length(), 22);
        n.confirm();
        n.confirm();
        n.confirm();
        assertEquals(OriginalWorldSettings.ORIGINAL_NEW_WORLD, n.setup().world);
        assertEquals("KIgn", n.setup().world.getCode());
    }

    /** The measured places: twelve pictures 72x48, the footer's line. */
    public void testGeometry() {
        assertEquals(4, ClassicNewWorldScreens.WORLD_X.length);
        assertEquals(3, ClassicNewWorldScreens.WORLD_Y.length);
        assertEquals("10,16,72,48", join(ClassicNewWorldScreens.worldCell(0, 0)));
        assertEquals("238,135,72,48", join(ClassicNewWorldScreens.worldCell(3, 2)));
        assertEquals("86,76,72,48", join(ClassicNewWorldScreens.worldCell(1, 1)));
        for (int c = 0; c < 4; c++) for (int r = 0; r < 3; r++) {
            final int[] cell = ClassicNewWorldScreens.worldCell(c, r);
            final int[] at = ClassicNewWorldScreens.worldCellAt(cell[0], cell[1]);
            assertEquals(c, at[0]);
            assertEquals(r, at[1]);
            assertNull(ClassicNewWorldScreens.worldCellAt(cell[0] - 1, cell[1]));
            assertNull(ClassicNewWorldScreens.worldCellAt(cell[0] + cell[2], cell[1] + cell[3] - 1));
            assertNull(ClassicNewWorldScreens.worldCellAt(cell[0], cell[1] + cell[3]));
        }
        assertEquals(new Rectangle(105, 189, 110, 7), ClassicNewWorldScreens.WORLD_FOOTER);
        assertEquals(0xDFBA00, ClassicNewWorldScreens.WORLD_CURRENT);
        assertEquals(0x04B610, ClassicNewWorldScreens.WORLD_CHOSEN);
    }

    private static String join(int[] a) {
        final StringBuilder sb = new StringBuilder();
        for (int v : a) sb.append(sb.length() == 0 ? "" : ",").append(v);
        return sb.toString();
    }

    /** The screen's words in the pack: four questions, twelve answers. */
    public void testTheWordsOfThePack() {
        final ClassicPackFiles p = ClassicPackFiles.runtime();
        final ClassicText t = ClassicText.load(p);
        if (t == null) {
            System.err.println(getClass().getSimpleName()
                + ": pack words skipped, no pack texts (ant classic-assets)");
            return;
        }
        final ClassicNewWorldScreens.World w = ClassicNewWorldScreens.World.load(p, t);
        assertNotNull(w);
        assertEquals(320, w.picture.getWidth());
        assertEquals(200, w.picture.getHeight());
        assertEquals(4, w.questions.length);
        for (int c = 0; c < 4; c++) {
            assertTrue(w.questions[c].endsWith(":"));
            assertEquals(3, w.answers[c].length);
            for (String a : w.answers[c]) assertFalse(a.isEmpty());
        }
        // LANDFORM's and KLIMA's middle answers are the same word, the
        // middle LANDMASSE is another (the index convention holds).
        assertEquals(w.answers[1][1], w.answers[3][1]);
        assertFalse(w.answers[0][1].equals(w.answers[1][1]));
        assertFalse(w.heading.isEmpty());
        assertEquals(w.heading, w.heading.toUpperCase());
    }

    /**
     * The title menu: INDIVIDUALISIEREN opens the world screen (before P3
     * a notice), Roger's clicks work through the real panel, the footer
     * goes on, Escape goes back, and back from the world screen bars
     * INDIVIDUALISIEREN on the title; AMERIKA still has its notice.
     */
    public void testTheTitleOpensTheWorldScreen() throws Exception {
        if (ClassicPackFiles.runtime() == null
            || ClassicText.load(ClassicPackFiles.runtime()) == null) {
            System.err.println(getClass().getSimpleName()
                + ": title check skipped, no pack (ant classic-assets)");
            return;
        }
        final List<ClassicGUI.NewWorldSetup> started = new ArrayList<>();
        final ClassicMainMenuPanel panel = new ClassicMainMenuPanel(
            new ClassicMainMenuPanel.Actions() {
                @Override public void newWorld(ClassicGUI.NewWorldSetup s) { started.add(s); }
                @Override public void loadGame(File f) {}
                @Override public void hallOfFame() {}
                @Override public void quit() {}
            });
        panel.setSize(320, 200);
        panel.showTitle(null);
        assertEquals(ClassicMainMenuPanel.Mode.TITLE, panel.mode());
        click(panel, titleRow(ClassicMainMenuPanel.ITEM_CUSTOMIZE));
        assertEquals(ClassicMainMenuPanel.Mode.NEW_WORLD, panel.mode());
        assertEquals(Step.WORLD, panel.chainView().step);
        assertEquals(1, panel.chainView().worldRows[0]);
        // The panel paints the world screen (no arrow: the pointer is off).
        final ClassicPackFiles p = ClassicPackFiles.runtime();
        final ClassicText text = ClassicText.load(p);
        final ClassicNewWorldScreens.Assets a = ClassicNewWorldScreens.Assets.load(p);
        final ClassicNewWorldScreens.Texts t = ClassicNewWorldScreens.Texts.load(text);
        final ClassicNewWorldScreens.World w = ClassicNewWorldScreens.World.load(p, text);
        final BufferedImage want = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final Graphics2D gw = want.createGraphics();
        ClassicNewWorldScreens.paintWorld(gw, a, t, w, new int[] { 1, 1, 1, 1 }, 0);
        gw.dispose();
        final BufferedImage got = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final Graphics2D gg = got.createGraphics();
        panel.paint(gg);
        gg.dispose();
        assertEquals("the panel's world screen", 0, differing(got, want));
        // The keys (an assumption): Right, Down, Left, Up, Enter.
        key(panel, KeyEvent.VK_RIGHT);
        key(panel, KeyEvent.VK_DOWN);
        assertEquals(1, panel.chainView().worldColumn);
        assertEquals(2, panel.chainView().worldRows[1]);
        key(panel, KeyEvent.VK_LEFT);
        key(panel, KeyEvent.VK_UP);
        assertEquals(0, panel.chainView().worldColumn);
        assertEquals(0, panel.chainView().worldRows[0]);
        key(panel, KeyEvent.VK_ENTER);
        assertEquals(Step.DIFFICULTY, panel.chainView().step);
        key(panel, KeyEvent.VK_ESCAPE);
        assertEquals(Step.WORLD, panel.chainView().step);
        press(panel, 34, 100);           // back to Mittelgroß
        assertEquals(1, panel.chainView().worldRows[0]);
        press(panel, 47, 156);
        assertEquals(2, panel.chainView().worldRows[0]);
        press(panel, 123, 157);
        assertEquals(1, panel.chainView().worldColumn);
        press(panel, 194, 192);
        assertEquals(Step.DIFFICULTY, panel.chainView().step);
        key(panel, KeyEvent.VK_ESCAPE);
        assertEquals(Step.WORLD, panel.chainView().step);
        assertEquals(2, panel.chainView().worldRows[1]);
        key(panel, KeyEvent.VK_ESCAPE);
        assertEquals(ClassicMainMenuPanel.Mode.TITLE, panel.mode());
        assertEquals(ClassicMainMenuPanel.ITEM_CUSTOMIZE, panel.selectedItem());
        assertNull(panel.chainView());
        // Open again: the middle row again (027 after 024).
        click(panel, titleRow(ClassicMainMenuPanel.ITEM_CUSTOMIZE));
        assertEquals(1, panel.chainView().worldRows[0]);
        assertEquals(0, panel.chainView().worldColumn);
        key(panel, KeyEvent.VK_ESCAPE);
        // NEUE WELT opens the difficulty screen; back bars NEUE WELT.
        click(panel, titleRow(ClassicMainMenuPanel.ITEM_NEW_WORLD));
        assertEquals(Step.DIFFICULTY, panel.chainView().step);
        key(panel, KeyEvent.VK_ESCAPE);
        assertEquals(ClassicMainMenuPanel.ITEM_NEW_WORLD, panel.selectedItem());
        // AMERIKA: still the notice.
        click(panel, titleRow(ClassicMainMenuPanel.ITEM_AMERICA));
        assertEquals(ClassicMainMenuPanel.Mode.NOTICE, panel.mode());
        assertTrue(started.isEmpty());
    }

    /**
     * Through the real panel to the game's start: INDIVIDUALISIEREN, the
     * clicks of {@code 024} (everything at the bottom), the footer, then
     * the chain by keys (the first difficulty, Holland, the default name,
     * the pages, the audience) and a click that skips the departure: the
     * engine is asked for Holland in Roger's world GCwf; NEUE WELT the
     * same way for KIgn.  On the event thread, so the departure's timer
     * cannot run in between.
     */
    public void testTheChoicesReachTheGame() throws Exception {
        if (ClassicPackFiles.runtime() == null
            || ClassicText.load(ClassicPackFiles.runtime()) == null) {
            System.err.println(getClass().getSimpleName()
                + ": start check skipped, no pack (ant classic-assets)");
            return;
        }
        final List<ClassicGUI.NewWorldSetup> started = new ArrayList<>();
        final Throwable[] failure = new Throwable[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try {
                for (boolean world : new boolean[] { true, false }) {
                    final ClassicMainMenuPanel panel = new ClassicMainMenuPanel(
                        new ClassicMainMenuPanel.Actions() {
                            @Override public void newWorld(ClassicGUI.NewWorldSetup s) {
                                started.add(s);
                            }
                            @Override public void loadGame(File f) {}
                            @Override public void hallOfFame() {}
                            @Override public void quit() {}
                        });
                    panel.setSize(320, 200);
                    panel.showTitle(null);
                    final int before = started.size();
                    click(panel, titleRow(world ? ClassicMainMenuPanel.ITEM_CUSTOMIZE
                                          : ClassicMainMenuPanel.ITEM_NEW_WORLD));
                    if (world) {
                        for (int[] k : new int[][] { { 47, 156 }, { 123, 157 }, { 199, 156 },
                                                     { 261, 155 }, { 194, 192 } }) {
                            press(panel, k[0], k[1]);
                        }
                    }
                    assertEquals(Step.DIFFICULTY, panel.chainView().step);
                    key(panel, KeyEvent.VK_ENTER);
                    for (int i = 0; i < 3; i++) key(panel, KeyEvent.VK_RIGHT);
                    key(panel, KeyEvent.VK_ENTER);
                    assertEquals(Step.NAME, panel.chainView().step);
                    key(panel, KeyEvent.VK_ENTER);
                    key(panel, KeyEvent.VK_SPACE);
                    key(panel, KeyEvent.VK_SPACE);
                    assertEquals(Step.AUDIENCE, panel.chainView().step);
                    key(panel, KeyEvent.VK_SPACE);
                    if (panel.mode() == ClassicMainMenuPanel.Mode.DEPARTURE) {
                        press(panel, 160, 100);
                    }
                    assertEquals(ClassicMainMenuPanel.Mode.STARTING, panel.mode());
                    assertEquals(before + 1, started.size());
                }
            } catch (Throwable t) {
                failure[0] = t;
            }
        });
        if (failure[0] instanceof Error) throw (Error) failure[0];
        if (failure[0] instanceof Exception) throw (Exception) failure[0];
        assertEquals(2, started.size());
        assertEquals("GCwf", started.get(0).world.getCode());
        assertEquals("model.nation.dutch", started.get(0).nationId);
        assertEquals("model.difficulty.veryEasy", started.get(0).difficultyId);
        assertEquals("KIgn", started.get(1).world.getCode());
        assertEquals("model.nation.dutch", started.get(1).nationId);
    }

    private static int[] titleRow(int item) {
        final Rectangle r = ClassicMenuBox.rowHitRect(ClassicMainMenuPanel.titleBounds(5), 1, item);
        return new int[] { r.x + r.width / 2, r.y + r.height / 2 };
    }

    /** A left press and release, straight to the panel's mouse listeners. */
    private static void click(ClassicMainMenuPanel p, int[] at) {
        press(p, at[0], at[1]);
        final MouseEvent e = new MouseEvent(p, MouseEvent.MOUSE_RELEASED,
            System.currentTimeMillis(), 0, at[0], at[1], 1, false, MouseEvent.BUTTON1);
        for (java.awt.event.MouseListener l : p.getMouseListeners()) l.mouseReleased(e);
    }

    private static void press(ClassicMainMenuPanel p, int x, int y) {
        final MouseEvent e = new MouseEvent(p, MouseEvent.MOUSE_PRESSED,
            System.currentTimeMillis(), MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false,
            MouseEvent.BUTTON1);
        for (java.awt.event.MouseListener l : p.getMouseListeners()) l.mousePressed(e);
    }

    /** A key press and release, straight to the panel's key listeners. */
    private static void key(ClassicMainMenuPanel p, int code) {
        final KeyEvent d = new KeyEvent(p, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
            0, code, KeyEvent.CHAR_UNDEFINED);
        for (java.awt.event.KeyListener l : p.getKeyListeners()) l.keyPressed(d);
        final KeyEvent u = new KeyEvent(p, KeyEvent.KEY_RELEASED, System.currentTimeMillis(),
            0, code, KeyEvent.CHAR_UNDEFINED);
        for (java.awt.event.KeyListener l : p.getKeyListeners()) l.keyReleased(u);
    }

    /**
     * Golden (the pack and {@code -Dclassic.clips}): the screen as the
     * production painter draws it, the original's arrow at the recorded
     * place, over twelve frames of the land-test clips -- the opening
     * state (024 #321, 026 #246), every answer row of every column that
     * Roger chose, each current column, the arrow over the footer (024
     * #1022, 027 #2712) -- and the difficulty screen that follows (024
     * #1072): 0 differing pixels.  (Of all 973 settled frames of the
     * screen in the eight clips, 873 are 0 px, the other 98 + 2 differ only
     * by an arrow caught mid-move or by the wipe that leaves the screen:
     * the scratch harness, p/p3/wp-run6.txt.)
     */
    public void testAgainstTheLandTestClips() throws Exception {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File root = (clips == null || clips.isBlank()) ? null : new File(clips, "landtests");
        if (root == null || !root.isDirectory()) {
            System.err.println(getClass().getSimpleName()
                + ": golden check skipped, no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ")");
            return;
        }
        final ClassicPackFiles p = ClassicPackFiles.runtime();
        final ClassicText text = ClassicText.load(p);
        final ClassicNewWorldScreens.Assets a = ClassicNewWorldScreens.Assets.load(p);
        final ClassicNewWorldScreens.Texts t = ClassicNewWorldScreens.Texts.load(text);
        final ClassicNewWorldScreens.World w = ClassicNewWorldScreens.World.load(p, text);
        if (a == null || t == null || w == null || a.cursor == null) {
            System.err.println(getClass().getSimpleName()
                + ": golden check skipped, no pack (ant classic-assets)");
            return;
        }
        // clip, frame, rows of the four columns / current column, arrow x, y
        final Object[][] frames = {
            { "024", 321, "1111/0", 152, 128 }, { "024", 461, "2111/0", 115, 157 },
            { "024", 499, "2211/1", 123, 157 }, { "024", 725, "2221/2", 199, 156 },
            { "024", 882, "2222/3", 261, 155 }, { "024", 1022, "2222/3", 194, 192 },
            { "026", 246, "1111/0", 133, 128 }, { "027", 2525, "0111/0", 34, 39 },
            { "027", 2712, "0111/0", 197, 193 }, { "029", 537, "1211/1", 133, 142 },
            { "030", 381, "1011/1", 116, 37 }, { "032", 326, "2111/0", 52, 162 },
        };
        final StringBuilder fails = new StringBuilder();
        for (Object[] f : frames) {
            final String s = (String) f[2];
            final int[] rows = new int[4];
            for (int i = 0; i < 4; i++) rows[i] = s.charAt(i) - '0';
            final BufferedImage ours = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
            final Graphics2D g = ours.createGraphics();
            ClassicNewWorldScreens.paintWorld(g, a, t, w, rows, s.charAt(5) - '0');
            g.drawImage(a.cursor, (Integer) f[3], (Integer) f[4], null);
            g.dispose();
            final int d = differing(ours, new File(root, (String) f[0]), (Integer) f[1]);
            if (d != 0) fails.append(' ').append(f[0]).append(" #").append(f[1]).append(": ").append(d);
        }
        // The difficulty screen after the footer, through the chain.
        final ClassicNewWorldChain c = ClassicNewWorldChain.create(t, a.intro, true);
        for (int[] k : new int[][] { { 47, 156 }, { 123, 157 }, { 199, 156 }, { 261, 155 },
                                     { 194, 192 } }) {
            c.clickPrimary(k[0], k[1]);
        }
        final BufferedImage ours = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = ours.createGraphics();
        ClassicNewWorldScreens.paint(g, a, t, w, c.view());
        g.drawImage(a.cursor, 194, 192, null);
        g.dispose();
        final int d = differing(ours, new File(root, "024"), 1072);
        if (d != 0) fails.append(" 024 #1072 (difficulty): ").append(d);
        assertEquals("differing pixels:" + fails, 0, fails.length());
    }

    private static int differing(BufferedImage ours, File clip, int frame) throws Exception {
        final File f = ClassicTerrainGoldenTest.frameFile(clip, frame);
        assertNotNull(clip + " #" + frame, f);
        return differing(ours, ImageIO.read(f));
    }

    private static int differing(BufferedImage a, BufferedImage b) {
        int n = 0;
        for (int y = 0; y < 200; y++) for (int x = 0; x < 320; x++) {
            if ((a.getRGB(x, y) & 0xFFFFFF) != (b.getRGB(x, y) & 0xFFFFFF)) n++;
        }
        return n;
    }
}
