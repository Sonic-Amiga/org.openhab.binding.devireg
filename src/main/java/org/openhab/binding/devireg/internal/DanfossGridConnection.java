package org.openhab.binding.devireg.internal;

import javax.xml.bind.DatatypeConverter;

import org.opensdg.OSDGConnection;
import org.opensdg.OSDGResult;
import org.opensdg.OSDGState;
import org.opensdg.OpenSDG;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DanfossGridConnection extends OSDGConnection {
    private static final Logger logger = LoggerFactory.getLogger(DanfossGridConnection.class);
    private static boolean g_KeyLoaded = false;
    private static DanfossGridConnection g_Conn;
    private static int numUsers = 0;

    public synchronized static DanfossGridConnection get() {
        // FIXME: Figure out why i don't see logs with higher levels and use info here
        if (!g_KeyLoaded) {
            DeviRegBindingConfig config = DeviRegBindingConfig.get();

            if (config.privateKey == null || config.privateKey.isEmpty()) {
                byte[] privateKey = OpenSDG.CreatePrivateKey();
                OpenSDG.SetPrivateKey(privateKey);

                config.privateKey = DatatypeConverter.printHexBinary(privateKey);
                config.publicKey = DatatypeConverter.printHexBinary(OpenSDG.GetMyPeerId());
            } else {
                OpenSDG.SetPrivateKey(DatatypeConverter.parseHexBinary(config.privateKey));
            }

            g_KeyLoaded = true;
        }

        if (g_Conn == null) {
            g_Conn = new DanfossGridConnection();
            g_Conn.SetBlockingMode(true);
        }

        if (g_Conn.getState() != OSDGState.CONNECTED) {
            OSDGResult res = g_Conn.ConnectToDanfoss();
            if (res != OSDGResult.NO_ERROR) {
                logger.error("Could not connect to Danfoss grid");
                return null; // TODO: throw
            }
            logger.warn("Successfully connected to Danfoss grid");
        }

        return g_Conn;
    }

    public static synchronized void AddUser() {
        numUsers++;
    }

    public static synchronized void RemoveUser() {
        if (--numUsers > 0) {
            return;
        }

        if (g_Conn == null) {
            return;
        }

        logger.warn("Last user is gone, disconnecting from Danfoss grid");
        g_Conn.Close();
        g_Conn.Dispose();
        g_Conn = null;
    }
}
