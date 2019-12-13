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
    static final DeviRegBindingConfig g_Config = new DeviRegBindingConfig();
    private static boolean g_KeyLoaded = false;
    private static DanfossGridConnection g_Conn;
    private static int numUsers = 0;

    public synchronized static DanfossGridConnection get() {
        byte[] privateKey;

        // FIXME: Figure out why i don't see logs with higher levels and use info here
        if (!g_KeyLoaded) {
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
