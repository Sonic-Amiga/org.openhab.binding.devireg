package org.openhab.binding.devireg.internal;

import org.opensdg.OSDGConnection;
import org.opensdg.OSDGResult;
import org.opensdg.OSDGState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DanfossGridConnection extends OSDGConnection {
    private static final Logger logger = LoggerFactory.getLogger(DanfossGridConnection.class);
    private static DanfossGridConnection g_Conn;
    private static int numUsers = 0;

    private String privateKey;

    public synchronized static DanfossGridConnection get() {
        if (g_Conn == null) {
            g_Conn = new DanfossGridConnection();
            g_Conn.SetBlockingMode(true);
            g_Conn.SetPrivateKey(DeviRegBindingConfig.get());
        }

        if (g_Conn.getState() != OSDGState.CONNECTED) {
            OSDGResult res = g_Conn.ConnectToDanfoss();
            if (res != OSDGResult.NO_ERROR) {
                logger.error("Could not connect to Danfoss grid");
                return null; // TODO: throw
            }
            logger.info("Successfully connected to Danfoss grid");
        }

        return g_Conn;
    }

    private void SetPrivateKey(DeviRegBindingConfig config) {
        privateKey = config.privateKey;
        SetPrivateKey(SDGUtils.ParseKey(privateKey));
    }

    public static synchronized void UpdatePrivateKey() {
        if (g_Conn != null) {
            DeviRegBindingConfig config = DeviRegBindingConfig.get();

            if (!config.privateKey.equals(g_Conn.privateKey)) {
                g_Conn.Close(); // Will reconnect on demand
                g_Conn.SetPrivateKey(config);
            }
        }
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
