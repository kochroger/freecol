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

import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.server.FreeColServer;


/**
 * The true terrain of the fog ring (M1c design 10 §5, item W6d).
 *
 * <p>The original draws the edge of the unexplored area from what its
 * map array really holds there: an unexplored tile next to explored water
 * shows its own land in its 3-px fringe, explored water carries coast
 * quarters toward land that is still dark, and explored tiles blend with
 * the true terrain of a dark neighbour (landfall #1407, fog-start #19 and
 * #876).  FreeCol's client has no type for an unexplored tile
 * ({@link Tile#isExplored}: {@code type == null}).  In single player the
 * server runs in the same JVM, so this oracle reads its map, read only.
 *
 * <p>{@link #trueType} answers:
 * <ul>
 *   <li>an <b>explored</b> tile: the client's type, what the player
 *       knows (even when the server's has changed since);</li>
 *   <li>an unexplored tile of the <b>fog ring</b> (within Chebyshev
 *       distance 1 of an explored tile) in <b>single player</b>: the
 *       server's type, as the client specification's object of the same
 *       id (the client reads its own copy of the specification);</li>
 *   <li>everything else: null, "unknown".  That is every tile beyond the
 *       ring, every tile in multiplayer (also for the host, whose client
 *       has a server too: Critic 13) and every tile while the server's
 *       game is not the client's (another UUID or map size, e.g. while a
 *       game loads).  The composer then uses fallback F-W6d-MP.</li>
 * </ul>
 *
 * <p><b>Least knowledge.</b>  Only the terrain composer may ask (fringe,
 * blend, coast bits and overlay connectivity, all within the ring); the
 * minimap and every other consumer keep using {@link Tile#isExplored} and
 * never see this class.  The ring is checked here as well, against the
 * client's explored state: a question beyond it is refused (null), counted
 * ({@link #refusals}) and logged once, so a composer bug shows instead of
 * leaking the map.  The client's state is never behind what the viewer
 * shows (the model runs ahead during server-pushed slides, Critic 5), so
 * the check never refuses a legitimate question.
 *
 * <p>Nothing is cached: the games are looked up through the suppliers on
 * every call, so loading a game switches maps.  Reading another thread's
 * {@code Tile.type} is safe enough for a picture: the reference is read
 * atomically, and the server changes unexplored types only through AI
 * terraforming, which the original's map array would show as well.
 * Every server access is guarded ({@code RuntimeException} gives null,
 * logged once).
 */
final class ClassicTerrainOracle {

    private static final Logger logger = Logger.getLogger(ClassicTerrainOracle.class.getName());

    /** The client's game: what the player knows. */
    private final Supplier<Game> clientGame;

    /** Whether the client plays a single-player game. */
    private final BooleanSupplier singlePlayer;

    /** The server's game in this JVM, or null without one. */
    private final Supplier<Game> serverGame;

    /** Set by {@link #dispose}: every answer is null from then on. */
    private volatile boolean disposed = false;

    /** Questions refused because they lie beyond the fog ring. */
    private final AtomicInteger refused = new AtomicInteger();

    /** Whether a refusal, or a failed server access, was logged. */
    private volatile boolean refusalLogged = false, failureLogged = false;


    /**
     * Create an oracle.  Package-private for the tests, which need no
     * {@code FreeColClient} (Critic 13).
     *
     * @param clientGame The client's game (may supply null).
     * @param singlePlayer Whether the game is a single-player game.
     * @param serverGame The server's game in this JVM (may supply null).
     */
    ClassicTerrainOracle(Supplier<Game> clientGame, BooleanSupplier singlePlayer,
                         Supplier<Game> serverGame) {
        this.clientGame = Objects.requireNonNull(clientGame);
        this.singlePlayer = Objects.requireNonNull(singlePlayer);
        this.serverGame = Objects.requireNonNull(serverGame);
    }

    /**
     * The oracle of a client: its game, its single-player flag and the
     * game of the server it started ({@link FreeColClient#getFreeColServer}).
     *
     * @param fcc The client (null: an oracle that knows nothing).
     * @return The oracle.
     */
    static ClassicTerrainOracle of(final FreeColClient fcc) {
        if (fcc == null) return new ClassicTerrainOracle(() -> null, () -> false, () -> null);
        return new ClassicTerrainOracle(fcc::getGame, fcc::getSinglePlayer, () -> {
                final FreeColServer server = fcc.getFreeColServer();
                return (server == null) ? null : server.getGame();
            });
    }


    /**
     * The terrain type the map is drawn with at a tile.
     *
     * @param x The tile's column.
     * @param y The tile's row.
     * @return The client's type of an explored tile; the server's true
     *     type of an unexplored tile in the fog ring (single player only);
     *     otherwise null (unknown, off the map, beyond the ring).
     */
    TileType trueType(int x, int y) {
        if (this.disposed) return null;
        final Map map = clientMap();
        final Tile tile = (map == null) ? null : map.getTile(x, y);
        if (tile == null) return null;
        final TileType known = tile.getType();
        if (known != null) return known;
        if (!inRing(map, x, y)) {
            refuse(x, y);
            return null;
        }
        return serverType(map, x, y);
    }

    /**
     * Whether a tile is an unexplored tile of the fog ring: unexplored,
     * and an explored tile lies within Chebyshev distance 1 (the 8 raw-grid
     * neighbours, the classic map's square grid).
     *
     * @param map The client's map.
     * @param x The tile's column.
     * @param y The tile's row.
     * @return True for a ring tile.
     */
    static boolean inRing(Map map, int x, int y) {
        final Tile tile = map.getTile(x, y);
        if (tile == null || tile.isExplored()) return false;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                final Tile n = map.getTile(x + dx, y + dy);
                if (n != null && n.isExplored()) return true;
            }
        }
        return false;
    }

    /**
     * Whether {@link #trueType} reads the server's map now: single player,
     * a server game that is the client's game (the same UUID and map size).
     *
     * @return True when ring tiles get their true type.
     */
    boolean knowsTruth() {
        if (this.disposed) return false;
        final Map map = clientMap();
        return map != null && serverMap(map) != null;
    }

    /** @return The number of questions refused beyond the fog ring. */
    int refusals() {
        return this.refused.get();
    }

    /**
     * The fog ring of the client's map in one line, for the log and the
     * recorder ({@code oracle} event and summary line), e.g.
     * {@code server ring=11 known=11 refused=0 highSeas=5 ocean=6}, or
     * {@code unknown ring=11 ...} without the truth (multiplayer, no
     * server, another game).  The type names are the ids without
     * {@code model.tile.}.  Walks the whole map; asks nothing beyond the
     * ring.
     *
     * @return The census.
     */
    String census() {
        final Map map = (this.disposed) ? null : clientMap();
        if (map == null) return "none";
        int ring = 0, known = 0;
        final java.util.Map<String, Integer> types = new TreeMap<>();
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                if (!inRing(map, x, y)) continue;
                ring++;
                final TileType t = serverType(map, x, y);
                if (t == null) continue;
                known++;
                types.merge(t.getSuffix(), 1, Integer::sum);
            }
        }
        final StringBuilder sb = new StringBuilder(knowsTruth() ? "server" : "unknown");
        sb.append(" ring=").append(ring).append(" known=").append(known)
            .append(" refused=").append(refusals());
        for (Entry<String, Integer> e : types.entrySet()) {
            sb.append(' ').append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /**
     * Drop the oracle with its game view ({@code ClassicGUI.teardownInGame}):
     * every answer is null from then on.
     */
    void dispose() {
        this.disposed = true;
    }


    // Internals

    /** @return The client's map, or null. */
    private Map clientMap() {
        try {
            final Game g = this.clientGame.get();
            return (g == null) ? null : g.getMap();
        } catch (RuntimeException e) {
            failed(e);
            return null;
        }
    }

    /**
     * The server's map, if it may be read for the client's map.
     *
     * @param clientMap The client's map.
     * @return The server's map of the same game, or null.
     */
    private Map serverMap(Map clientMap) {
        try {
            if (!this.singlePlayer.getAsBoolean()) return null;
            final Game sg = this.serverGame.get();
            final Game cg = this.clientGame.get();
            if (sg == null || cg == null
                || !Objects.equals(sg.getUUID(), cg.getUUID())) return null;
            final Map sm = sg.getMap();
            return (sm == null || sm.getWidth() != clientMap.getWidth()
                || sm.getHeight() != clientMap.getHeight()) ? null : sm;
        } catch (RuntimeException e) {
            failed(e);
            return null;
        }
    }

    /**
     * The server's type of a tile, as the client specification's object.
     *
     * @param clientMap The client's map.
     * @param x The tile's column.
     * @param y The tile's row.
     * @return The type, or null without the server's map.
     */
    private TileType serverType(Map clientMap, int x, int y) {
        final Map sm = serverMap(clientMap);
        if (sm == null) return null;
        try {
            final Tile st = sm.getTile(x, y);
            final TileType t = (st == null) ? null : st.getType();
            if (t == null) return null;
            final Game cg = this.clientGame.get();
            final Specification spec = (cg == null) ? null : cg.getSpecification();
            final TileType c = (spec == null) ? null : spec.getTileType(t.getId());
            return (c != null) ? c : t;
        } catch (RuntimeException e) {
            failed(e);
            return null;
        }
    }

    private void refuse(int x, int y) {
        this.refused.incrementAndGet();
        if (!this.refusalLogged) {
            this.refusalLogged = true;
            logger.warning("ClassicTerrainOracle: refused (" + x + "," + y
                + "), an unexplored tile beyond the fog ring (least knowledge,"
                + " M1c design 10, section 5); further refusals are only counted.");
        }
    }

    private void failed(RuntimeException e) {
        if (!this.failureLogged) {
            this.failureLogged = true;
            logger.log(Level.WARNING, "ClassicTerrainOracle: game access failed,"
                + " the fog ring is drawn as unknown", e);
        }
    }
}
