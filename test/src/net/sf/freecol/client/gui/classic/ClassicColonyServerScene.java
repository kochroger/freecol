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
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.InGameController;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.networking.ChangeStateMessage;
import net.sf.freecol.common.networking.Connection;
import net.sf.freecol.common.networking.LoadGoodsMessage;
import net.sf.freecol.common.networking.PayForBuildingMessage;
import net.sf.freecol.common.networking.RenameMessage;
import net.sf.freecol.common.networking.ServerAPI;
import net.sf.freecol.common.networking.SetBuildQueueMessage;
import net.sf.freecol.common.networking.UnloadGoodsMessage;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * Part V's screen tests with FreeCol's real client controller and a real
 * server game (the part U and N1 pattern): the client's server requests go
 * to the server's own handlers for our player (the game objects are the
 * server's, so a change is seen at once), noted in a log; the screen's
 * boxes answered from a queue; synthetic mouse events at scale 3.
 */
final class ClassicColonyServerScene {

    private ClassicColonyServerScene() {}

    /** The client's server API: each request to the server's handler, logged. */
    static final class Loopback extends ServerAPI {

        final FreeColServer server;
        final ServerPlayer player;
        final List<String> log = new ArrayList<>();

        Loopback(FreeColServer server, ServerPlayer player) {
            this.server = server;
            this.player = player;
        }

        private boolean handle(String what, ChangeSet cs) {
            final String s = (cs == null) ? "" : cs.toString();
            final boolean bad = s.contains("error") || s.contains("reject");
            this.log.add(what + ((bad) ? " refused" : ""));
            return !bad;
        }

        @Override
        public boolean loadGoods(Location loc, GoodsType type, int amount, Unit carrier) {
            return handle("load " + amount + " " + type.getSuffix() + " " + carrier.getId(),
                new LoadGoodsMessage(loc, type, amount, carrier)
                    .serverHandler(this.server, this.player));
        }

        @Override
        public boolean unloadGoods(GoodsType type, int amount, Unit carrier) {
            return handle("unload " + amount + " " + type.getSuffix() + " " + carrier.getId(),
                new UnloadGoodsMessage(type, amount, carrier)
                    .serverHandler(this.server, this.player));
        }

        @Override
        public boolean setBuildQueue(Colony colony, List<BuildableType> queue) {
            return handle("buildQueue " + queue,
                new SetBuildQueueMessage(colony, queue).serverHandler(this.server, this.player));
        }

        @Override
        public boolean payForBuilding(Colony colony) {
            return handle("pay " + colony.getName(),
                new PayForBuildingMessage(colony).serverHandler(this.server, this.player));
        }

        @Override
        public boolean changeState(Unit unit, Unit.UnitState state) {
            return handle("state " + unit.getId() + " " + state,
                new ChangeStateMessage(unit, state).serverHandler(this.server, this.player));
        }

        @Override
        public boolean chooseFoundingFather(List<net.sf.freecol.common.model.FoundingFather> ffs,
                                            net.sf.freecol.common.model.FoundingFather ff) {
            return handle("father " + ((ff == null) ? "none" : ff.getId()),
                new net.sf.freecol.common.networking.ChooseFoundingFatherMessage(ffs, ff)
                    .serverHandler(this.server, this.player));
        }

        @Override
        public boolean rename(FreeColGameObject object, String name) {
            return handle("rename " + name,
                new RenameMessage(object, name).serverHandler(this.server, this.player));
        }

        @Override
        public Connection connect(String name, String host, int port) {
            return null;
        }

        @Override
        public boolean disconnect() {
            return true;
        }

        @Override
        public Connection reconnect() {
            return null;
        }

        @Override
        public Connection getConnection() {
            return null;
        }
    }

    private static void set(FreeColClient fcc, String name, Object value) throws Exception {
        final Field f = FreeColClient.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(fcc, value);
    }

    /**
     * A client of the server's game with FreeCol's real controller.
     *
     * @param game The game.
     * @param me Our player.
     * @param api The loopback.
     * @return The client.
     * @exception Exception if it cannot be made.
     */
    static FreeColClient client(Game game, ServerPlayer me, Loopback api) throws Exception {
        final Class<?> uc = Class.forName("sun.misc.Unsafe");
        final Field theUnsafe = uc.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        final FreeColClient fcc = (FreeColClient)uc
            .getMethod("allocateInstance", Class.class)
            .invoke(theUnsafe.get(null), FreeColClient.class);
        set(fcc, "gui", new GUI(fcc) {
                @Override
                public void playSound(String sound) {}
            });
        set(fcc, "serverAPI", api);
        set(fcc, "clientOptions", new ClientOptions() {
                @Override
                public boolean getBoolean(String id) {
                    return false;
                }
            });
        set(fcc, "inGameController", new InGameController(fcc));
        fcc.setGame(game);
        fcc.setMyPlayer(me);
        set(fcc, "inGame", Boolean.TRUE);
        game.setCurrentPlayer(me);
        return fcc;
    }

    /** The screen's boxes: the answers given in turn (an amount: its field's text), the requests kept. */
    static final class Boxes implements ClassicColonyPanel.Units {
        final Deque<Object> answers = new ArrayDeque<>();
        final List<ClassicAdvisorBox.Request> asked = new ArrayList<>();

        @Override
        public List<Unit> order(List<Unit> standing) {
            return standing;
        }

        @Override
        public void options(Unit unit, boolean first) {}

        @Override
        public int ask(ClassicAdvisorBox.Request r) {
            this.asked.add(r);
            final Object a = this.answers.poll();
            if (a instanceof String && r.field != null) {
                // Enter on a field with this text (an amount, a name).
                r.field.take((String) a);
                return 0;
            }
            if (a instanceof Help) {
                // F1 or a right-click on a row (fixer A after part X): as
                // the real bar, only on a row with a help.
                final int row = ((Help) a).row;
                if (r.help == null || !r.help.covers(row)) return ClassicAdvisorBox.Bar.OPEN;
                r.help.help(row);
                return ClassicAdvisorBox.Bar.HELP;
            }
            return (a instanceof Integer) ? (Integer) a : ClassicAdvisorBox.Bar.DISMISSED;
        }

        /** An answer: F1 or a right-click on a row. */
        static final class Help {
            final int row;

            Help(int row) {
                this.row = row;
            }
        }

        /** The Colonopedia pages asked (fixer A after part X): their types or rows, and delays. */
        final List<Object> pages = new ArrayList<>();
        final List<Double> pageDelays = new ArrayList<>();

        @Override
        public void buildingPage(net.sf.freecol.common.model.BuildingType type, double delayMs) {
            this.pages.add(type);
            this.pageDelays.add(delayMs);
        }

        @Override
        public void skillPage(int job, double delayMs) {
            this.pages.add(job);
            this.pageDelays.add(delayMs);
        }

        /** @return The last box. */
        ClassicAdvisorBox.Request last() {
            return this.asked.get(this.asked.size() - 1);
        }

        /** @return The last box's id and rows, plain. */
        String lastRows() {
            final ClassicAdvisorBox.Request r = last();
            return r.id + " " + Arrays.toString(r.plainRows()) + " bar=" + r.defaultRow;
        }
    }

    /** Paint the screen as Swing would (scale 3): the models and the hit places exist. */
    static BufferedImage paint(ClassicColonyPanel p) {
        final BufferedImage img = new BufferedImage(960, 600, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        p.paint(g);
        g.dispose();
        return img;
    }

    /** A synthetic mouse event at a canvas point (scale 3), dispatched to the screen. */
    static void mouse(ClassicColonyPanel p, int id, int vx, int vy, boolean shift) {
        int mods = (shift) ? InputEvent.SHIFT_DOWN_MASK : 0;
        if (id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_DRAGGED) {
            mods |= InputEvent.BUTTON1_DOWN_MASK;
        }
        final int button = (id == MouseEvent.MOUSE_DRAGGED) ? MouseEvent.NOBUTTON
            : MouseEvent.BUTTON1;
        p.dispatchEvent(new MouseEvent(p, id, System.currentTimeMillis(), mods,
                                       vx * 3 + 1, vy * 3 + 1, 1, false, button));
    }

    /** Press at a, drag over the way to b, release at b (with Shift or not). */
    static void drag(ClassicColonyPanel p, int ax, int ay, int bx, int by, boolean shift) {
        mouse(p, MouseEvent.MOUSE_PRESSED, ax, ay, shift);
        for (int i = 1; i <= 4; i++) {
            mouse(p, MouseEvent.MOUSE_DRAGGED, ax + (bx - ax) * i / 4, ay + (by - ay) * i / 4,
                  shift);
        }
        mouse(p, MouseEvent.MOUSE_RELEASED, bx, by, shift);
        paint(p);
    }

    /** Press and release at a point. */
    static void click(ClassicColonyPanel p, int vx, int vy) {
        mouse(p, MouseEvent.MOUSE_PRESSED, vx, vy, false);
        mouse(p, MouseEvent.MOUSE_RELEASED, vx, vy, false);
        paint(p);
    }
}
