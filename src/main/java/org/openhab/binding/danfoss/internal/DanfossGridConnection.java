package org.openhab.binding.danfoss.internal;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.opensdg.java.Connection;
import org.opensdg.java.GridConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DanfossGridConnection extends GridConnection {
    private static final Logger logger = LoggerFactory.getLogger(DanfossGridConnection.class);
    private static DanfossGridConnection g_Conn;
    private static int numUsers = 0;
    private static String privateKey = null;

    public synchronized static DanfossGridConnection get()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        if (privateKey == null) {
            privateKey = DanfossBindingConfig.get().privateKey;
        }
        if (g_Conn == null) {
            g_Conn = new DanfossGridConnection(SDGUtils.ParseKey(privateKey));
        }

        if (g_Conn.getState() != Connection.State.CONNECTED) {
            g_Conn.connect(GridConnection.Danfoss);
            logger.info("Successfully connected to Danfoss grid");
        }

        return g_Conn;
    }

    public DanfossGridConnection(byte[] privKey) {
        super(privKey);
    }

    public static synchronized void UpdatePrivateKey() {
        if (g_Conn != null) {
            DanfossBindingConfig config = DanfossBindingConfig.get();

            if (!config.privateKey.equals(privateKey)) {
                // Will reconnect on demand
                closeConnection();
                privateKey = config.privateKey;
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
        closeConnection();
    }

    private static void closeConnection() {
        g_Conn.close();
        g_Conn = null;
        logger.info("Grid connection closed");

    }

}
