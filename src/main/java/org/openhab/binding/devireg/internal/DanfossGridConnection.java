package org.openhab.binding.devireg.internal;

import javax.xml.bind.DatatypeConverter;

import org.eclipse.jdt.annotation.NonNull;
import org.opensdg.OSDGConnection;
import org.opensdg.OSDGResult;
import org.opensdg.OSDGState;
import org.opensdg.OpenSDG;
import org.slf4j.Logger;

public class DanfossGridConnection extends OSDGConnection {
    static final DeviRegBindingConfig g_Config = new DeviRegBindingConfig();
    private static boolean m_KeyLoaded = false;
    private static DanfossGridConnection m_Conn;

    public synchronized static DanfossGridConnection get(@NonNull Logger logger) {
        byte[] privateKey;

        // FIXME: Figure out why i don't see logs with higher levels and use info here
        if (!m_KeyLoaded) {
            if (g_Config.privateKey == null || g_Config.privateKey.isEmpty()) {
                privateKey = OpenSDG.CreatePrivateKey();
                g_Config.privateKey = DatatypeConverter.printHexBinary(privateKey);
                logger.warn("Created private key: " + g_Config.privateKey);
            } else {
                logger.warn("Using private key: " + g_Config.privateKey);
                privateKey = DatatypeConverter.parseHexBinary(g_Config.privateKey);
            }

            OpenSDG.SetPrivateKey(privateKey);

            g_Config.publicKey = DatatypeConverter.printHexBinary(OpenSDG.GetMyPeerId());
            logger.warn("My peer ID is:" + g_Config.publicKey);
        }

        if (m_Conn == null) {
            m_Conn = new DanfossGridConnection();
            m_Conn.SetBlockingMode(true);
        }

        if (m_Conn.getState() != OSDGState.CONNECTED) {
            OSDGResult res = m_Conn.ConnectToDanfoss();
            if (res != OSDGResult.NO_ERROR) {
                logger.error("Could not connect to Danfoss grid");
                return null; // TODO: throw
            }
            logger.warn("Successfully connected to Danfoss grid");
        }

        return m_Conn;
    }
}
