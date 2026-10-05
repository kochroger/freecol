/**
 *  Copyright (C) 2002-2024   The FreeCol Team
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

package net.sf.freecol.common.model;

import java.util.List;
import java.util.logging.Logger;

import static net.sf.freecol.common.util.CollectionUtils.*;


/**
 * The layout of the tiles of a {@link Map}, that is, which map
 * coordinates are the neighbours of a tile.
 *
 * FreeCol's own map is {@link #ISOMETRIC}: odd rows are shifted by half
 * a tile, so the neighbour in a given {@link Direction} depends on the
 * parity of the row, and two rows make up one step north or south.
 * {@link #SQUARE} is the chessboard layout of the original
 * Colonization, where every direction is a fixed one tile step.
 * Both layouts give every tile eight neighbours and measure distance
 * in "king moves", so the game rules do not change, only the mapping
 * from map coordinates to neighbours.
 *
 * Each {@link Map} stores its topology ({@link Map#getTopology}), and
 * saved games and maps carry it as the map's "topology" attribute.  A
 * map without the attribute (an old save, a bundled .fsm map) is
 * isometric.
 *
 * The code that steps between tiles, though, does not see a map:
 * {@link Direction#step}, {@code Map.Position}, the land generator
 * ({@link LandMap}, which runs before any {@code Map} exists) and the
 * flood fills.  So the topology in use is also a JVM wide setting,
 * {@link #current}, kept equal to the topology of the map in play:
 * <ul>
 *   <li>Reading a map sets it from the map, before any tile is read,
 *       so a save is always interpreted the way it was made.  This
 *       covers loading a game on the server, the client's copy of the
 *       game, the map editor and imported maps.</li>
 *   <li>A new map is stamped with the topology in use, so the code
 *       that starts a new game sets it before the map is generated:
 *       {@link #SQUARE} for the Classic UI, {@link #ISOMETRIC} for
 *       FreeCol's standard GUI and the map editor, which can only draw
 *       isometric maps.</li>
 *   <li>{@link Map#checkIntegrity} checks that the two agree.  Under
 *       the wrong topology {@link TileImprovement#checkIntegrity} would
 *       remove every river connection that does not match a
 *       neighbour, so a mismatch is logged as severe and, when fixing,
 *       resolved in favour of the map.</li>
 * </ul>
 *
 * The system property {@value #PROPERTY} ("square" or "isometric")
 * overrides the topology of new games, for developers and tests, and
 * is the topology a JVM starts with.  It never overrides the topology
 * stored in a map.
 */
public enum Topology {

    /** FreeCol's isometric layout, odd rows shifted by half a tile. */
    ISOMETRIC {

        /**
         * {@inheritDoc}
         */
        @Override
        public Map.Position step(Direction direction, int x, int y) {
            return direction.stepIsometric(x, y);
        }

        /**
         * {@inheritDoc}
         *
         * With an isometric map this is a non-trivial task.
         * The formula below has been developed largely through trial and
         * error.  It should cover all cases, but I wouldn't bet my
         * life on it.
         */
        @Override
        public int distance(int ax, int ay, int bx, int by) {
            int r = (bx - ax) - (ay - by) / 2;

            if (by > ay && ay % 2 == 0 && by % 2 != 0) {
                r++;
            } else if (by < ay && ay % 2 != 0 && by % 2 == 0) {
                r--;
            }
            return Math.max(Math.abs(ay - by + r), Math.abs(r));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<Direction> edgeDirections() {
            return Direction.longSides;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<Direction> cornerDirections() {
            return Direction.corners;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Direction ringDirection(Direction direction) {
            return direction;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int polarHeight() {
            return Map.POLAR_HEIGHT;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int rowFactor() {
            return 2;
        }
    },

    /** The square layout of the original game. */
    SQUARE {

        /**
         * {@inheritDoc}
         */
        @Override
        public Map.Position step(Direction direction, int x, int y) {
            return new Map.Position(x + SQUARE_DX[direction.ordinal()],
                                    y + SQUARE_DY[direction.ordinal()]);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int distance(int ax, int ay, int bx, int by) {
            return Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<Direction> edgeDirections() {
            return SQUARE_EDGES;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<Direction> cornerDirections() {
            return SQUARE_CORNERS;
        }

        /**
         * {@inheritDoc}
         *
         * The isometric ring walk turned by 45 degrees: NE becomes N,
         * SE becomes E and so on.
         */
        @Override
        public Direction ringDirection(Direction direction) {
            return direction.rotate(-1);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int polarHeight() {
            return 1;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int rowFactor() {
            return 1;
        }
    };

    private static final Logger logger = Logger.getLogger(Topology.class.getName());

    /**
     * The system property that overrides the topology of new games
     * ("square" or "isometric").
     */
    public static final String PROPERTY = "freecol.topology";

    /** Square step increments, indexed by {@code Direction.ordinal()}. */
    private static final int[] SQUARE_DX = {  0,  1, 1, 1, 0, -1, -1, -1 };
    private static final int[] SQUARE_DY = { -1, -1, 0, 1, 1,  1,  0, -1 };

    /** The directions to the tiles that share an edge on a square map. */
    private static final List<Direction> SQUARE_EDGES
        = makeUnmodifiableList(Direction.N, Direction.E,
                               Direction.S, Direction.W);

    /** The directions to the tiles that share a corner on a square map. */
    private static final List<Direction> SQUARE_CORNERS
        = makeUnmodifiableList(Direction.NE, Direction.SE,
                               Direction.SW, Direction.NW);

    /** The topology the {@value #PROPERTY} property asks for, or null. */
    private static final Topology OVERRIDE
        = fromProperty(System.getProperty(PROPERTY));

    /** The topology in use, that of the map in play. */
    private static volatile Topology current = getDefault();


    /**
     * Get the topology in use.
     *
     * @return The current {@code Topology}.
     */
    public static Topology current() {
        return current;
    }

    /**
     * Set the topology in use.  Called when a map is read, before a new
     * map is generated, and by tests, which must restore the previous
     * value when done.
     *
     * @param topology The new {@code Topology}.
     */
    public static void setCurrent(Topology topology) {
        if (topology == null) {
            throw new IllegalArgumentException("Null topology");
        }
        final Topology old = current;
        current = topology;
        if (old != topology) {
            logger.info("Map topology now " + topology + " (was " + old
                + ").");
        }
    }

    /**
     * Get the topology a JVM starts with: the one the {@value #PROPERTY}
     * property asks for, else {@link #ISOMETRIC}.
     *
     * @return The default {@code Topology}.
     */
    public static Topology getDefault() {
        return (OVERRIDE != null) ? OVERRIDE : ISOMETRIC;
    }

    /**
     * Get the topology to give a new game.  The {@value #PROPERTY}
     * property overrides the topology the caller prefers.
     *
     * @param preferred The {@code Topology} the caller would use.
     * @return The {@code Topology} for the new game.
     */
    public static Topology forNewGame(Topology preferred) {
        return (OVERRIDE != null) ? OVERRIDE : preferred;
    }

    /**
     * Get the topology named by a property value.
     *
     * @param value The value of the {@value #PROPERTY} property.
     * @return The {@code Topology} named, or null if none is.
     */
    private static Topology fromProperty(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        for (Topology t : values()) {
            if (t.name().equalsIgnoreCase(value.trim())) {
                logger.info("Property " + PROPERTY + ": new games use the "
                    + t + " map topology.");
                return t;
            }
        }
        logger.warning("Property " + PROPERTY + " ignored, not a topology: "
            + value);
        return null;
    }

    /**
     * Step the x and y coordinates in a direction.
     *
     * @param direction The {@code Direction} to step in.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @return The map position after the step.
     */
    public abstract Map.Position step(Direction direction, int x, int y);

    /**
     * Gets the distance in tiles between two map positions, that is,
     * the number of steps needed to get from one to the other.
     *
     * @param ax The x-coordinate of the first position.
     * @param ay The y-coordinate of the first position.
     * @param bx The x-coordinate of the second position.
     * @param by The y-coordinate of the second position.
     * @return The distance in tiles between the positions.
     */
    public abstract int distance(int ax, int ay, int bx, int by);

    /**
     * Gets the directions to the neighbours that share an edge with a
     * tile.  Rivers connect across these, and river styles are indexed
     * by this list.
     *
     * @return A list of four directions, clockwise.
     */
    public abstract List<Direction> edgeDirections();

    /**
     * Gets the directions to the neighbours that only share a corner
     * with a tile.
     *
     * @return A list of four directions, clockwise.
     */
    public abstract List<Direction> cornerDirections();

    /**
     * Gets the direction the circle iterator of a {@code Map} steps in
     * where its isometric walk steps in the given direction.
     *
     * @param direction The {@code Direction} of the isometric walk.
     * @return The {@code Direction} to step in with this topology.
     */
    public abstract Direction ringDirection(Direction direction);

    /**
     * Gets the number of tiles from the upper edge that are considered
     * polar by default.
     *
     * @return The polar height.
     */
    public abstract int polarHeight();

    /**
     * Gets the number of map rows that make up one step north or
     * south.  The land generator scales its vertical edge distances
     * with this.
     *
     * @return The row factor.
     */
    public abstract int rowFactor();
}
