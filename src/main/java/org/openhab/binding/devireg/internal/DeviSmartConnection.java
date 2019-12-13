package org.openhab.binding.devireg.internal;

import org.opensdg.OSDGConnection;
import org.opensdg.OSDGState;

public class DeviSmartConnection extends OSDGConnection {

    private DeviRegHandler m_Handler;

    DeviSmartConnection(DeviRegHandler handler) {
        m_Handler = handler;
    }

    @Override
    protected void onStatusChanged(OSDGState newState) {
        m_Handler.setOnlineStatus(newState == OSDGState.CONNECTED);
    }

}
