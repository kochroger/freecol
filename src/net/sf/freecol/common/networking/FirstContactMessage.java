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

package net.sf.freecol.common.networking;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ai.AIPlayer;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * The message sent when making first contact between players.
 */
public class FirstContactMessage extends AttributeMessage {

    public static final String TAG = "firstContact";
    private static final String CAMPS_TAG = "camps";
    private static final String OTHER_TAG = "other";
    private static final String PLAYER_TAG = "player";
    private static final String RESULT_TAG = "result";
    private static final String TILE_TAG = "tile";


    /**
     * Create a new {@code FirstContactMessage}.
     *
     * @param player The {@code Player} making contact.
     * @param other The {@code Player} being contacted.
     * @param tile An optional {@code Tile} to offer.
     */
    public FirstContactMessage(Player player, Player other, Tile tile) {
        super(TAG, PLAYER_TAG, player.getId(), OTHER_TAG, other.getId(),
              TILE_TAG, (tile == null) ? null : tile.getId(),
              CAMPS_TAG, ((other.isEuropean()) ? null
                  : String.valueOf(other.getSettlementCount())));
    }

    /**
     * Create a new {@code FirstContactMessage} from a stream.
     *
     * @param game The {@code Game} this message belongs to.
     * @param xr The {@code FreeColXMLReader} to read from.
     * @exception XMLStreamException if the stream is corrupt.
     */
    public FirstContactMessage(Game game, FreeColXMLReader xr)
        throws XMLStreamException {
        super(TAG, xr, PLAYER_TAG, OTHER_TAG, TILE_TAG, CAMPS_TAG, RESULT_TAG);
    }


    /**
     * {@inheritDoc}
     *
     * After the stance and the updates of the same reply, before the
     * owned changes and the late messages (the land's name): the client
     * may answer the question while it handles this reply (a contact made
     * by our own move, its box on the event thread), and the answer's own
     * reply (the Classic UI's war on «Nein», the tile given on «Ja») must
     * not be undone by this reply's provisional peace and its copy of the
     * tile, which came after it while this was EARLY (the O fixer, review
     * of part O; the unit also stands on its new tile while the chief
     * speaks).
     */
    @Override
    public MessagePriority getPriority() {
        return Message.MessagePriority.NORMAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void aiHandler(FreeColServer freeColServer, AIPlayer aiPlayer) {
        final Game game = freeColServer.getGame();
        final Player contactor = getPlayer(game);
        final Player contactee = getOtherPlayer(game);
        final Tile tile = getTile(game);

        aiPlayer.firstContactHandler(contactor, contactee, tile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clientHandler(FreeColClient freeColClient) {
        final Game game = freeColClient.getGame();
        final Player player = getPlayer(game);
        final Player other = getOtherPlayer(game);
        final Tile tile = getTile(game);
        final int n = getSettlementCount();

        if (player == null || player != freeColClient.getMyPlayer()) {
            logger.warning("firstContact with bad player: " + player);
            return;
        }
        if (other == null || other == player || !other.isIndian()) {
            logger.warning("firstContact with bad other player: " + other);
            return;
        }
        if (tile != null && tile.getOwner() != other) {
            logger.warning("firstContact with bad tile: " + tile);
            return;
        }

        igc(freeColClient).firstContactHandler(player, other, tile, n);
        clientGeneric(freeColClient);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChangeSet serverHandler(FreeColServer freeColServer,
                                   ServerPlayer serverPlayer) {
        final Game game = freeColServer.getGame();
        final String playerId = getStringAttribute(PLAYER_TAG);
        final String otherId = getStringAttribute(OTHER_TAG);

        Player first = getPlayer(game);
        if (first == null) {
            return serverPlayer.clientError("Invalid player: " + playerId);
        } else if (serverPlayer.getId().equals(playerId)) {
            ; // OK
        } else {
            return serverPlayer.clientError("Not our player: " + playerId);
        }

        Player otherPlayer = getOtherPlayer(game);
        if (otherPlayer == null) {
            return serverPlayer.clientError("Invalid other player: " + otherId);
        } else if (otherPlayer == (Player)serverPlayer) {
            return serverPlayer.clientError("First contact with self!?!");
        }

        // Proceed to contact.
        return igc(freeColServer)
            .nativeFirstContact(serverPlayer, otherPlayer,
                                getTile(game), getResult());
    }


    // Public interface

    public Player getPlayer(Game game) {
        return game.getFreeColGameObject(getStringAttribute(PLAYER_TAG), Player.class);
    }

    public Player getOtherPlayer(Game game) {
        return game.getFreeColGameObject(getStringAttribute(OTHER_TAG), Player.class);
    }

    public Tile getTile(Game game) {
        return game.getFreeColGameObject(getStringAttribute(TILE_TAG), Tile.class);
    }

    public int getSettlementCount() {
        return getIntegerAttribute(CAMPS_TAG, -1);
    }
            
    public boolean getResult() {
        return getBooleanAttribute(RESULT_TAG, (Boolean)null);
    }

    public FirstContactMessage setResult(boolean result) {
        setBooleanAttribute(RESULT_TAG, result);
        return this;
    }
}
