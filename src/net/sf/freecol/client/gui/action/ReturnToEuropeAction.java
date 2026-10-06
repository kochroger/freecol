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

package net.sf.freecol.client.gui.action;

import java.awt.event.ActionEvent;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.model.Unit;


/**
 * An action to send the active ship back to Europe: the original's
 * BEFEHLE order "Zurück nach Europa" (MENU.TXT), used by the classic
 * UI's menu and key.  It is the destination dialog's "Europe" without
 * the dialog ({@code InGameController.goToEurope}): a ship on the high
 * seas sails at once, any other one gets Europe as its destination and
 * sails on its way there.
 */
public class ReturnToEuropeAction extends UnitAction {

    public static final String id = "returnToEuropeAction";


    /**
     * Creates this action.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    public ReturnToEuropeAction(FreeColClient freeColClient) {
        super(freeColClient, id);
    }


    /**
     * Whether a unit may be sent back to Europe: a ship on the map that
     * can cross the high seas, of a player who still has Europe (not
     * after the declaration).  The original lists the order for ships
     * only.
     *
     * @param unit The {@code Unit} to check.
     * @return True if the order applies.
     */
    public static boolean canReturnToEurope(Unit unit) {
        return unit != null && unit.isNaval() && unit.hasTile()
            && unit.getType().canMoveToHighSeas()
            && unit.getOwner() != null && unit.getOwner().getEurope() != null;
    }


    // Override FreeColAction

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean shouldBeEnabled() {
        return super.shouldBeEnabled() && !getGUI().isPanelShowing()
            && canReturnToEurope(getGUI().getActiveUnit());
    }


    // Interface ActionListener

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(ActionEvent ae) {
        final Unit unit = getGUI().getActiveUnit();
        if (canReturnToEurope(unit)) igc().goToEurope(unit);
    }
}
