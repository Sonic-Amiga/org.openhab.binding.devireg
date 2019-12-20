package org.openhab.binding.devireg.internal;

import javax.xml.bind.DatatypeConverter;

import org.opensdg.OSDGConnection;
import org.opensdg.OSDGResult;
import org.opensdg.OSDGState;
import org.opensdg.protocol.DeviSmart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeviSmartConnection extends OSDGConnection {
    private final Logger logger = LoggerFactory.getLogger(DeviSmartConnection.class);

    private DeviRegHandler m_Handler;

    DeviSmartConnection(DeviRegHandler handler) {
        m_Handler = handler;
    }

    @Override
    protected void onStatusChanged(OSDGState newState) {
        m_Handler.setOnlineStatus(newState == OSDGState.CONNECTED);
    }

    @Override
    protected OSDGResult onDataReceived(byte[] data) {
        int offset = 0;
        int length = data.length;

        /*
         * For some reason the first data packet from the thermostat actually
         * consists of many merged messages. It looks like nothing forbids this
         * to be done at any moment. Also this suggests that garbage zero byte
         * in the beginning of this bunch could be a buffering bug.
         */
        while (length >= DeviSmart.Packet.HeaderSize) {
            DeviSmart.Packet pkt = new DeviSmart.Packet(data, offset);
            int packetLen = pkt.getLength();

            if (packetLen > length) {
                // Packet header specifies more bytes than we have. The packet is clearly malformed.
                logger.error("Malformed data at position {}; size exceeds buffer", offset);
                logger.error(DatatypeConverter.printHexBinary(data));
                break; // Drop the rest of data and continue
            }

            m_Handler.handlePacket(pkt);

            offset += packetLen;
            length -= packetLen;
        }

        return OSDGResult.NO_ERROR;
    }
}
