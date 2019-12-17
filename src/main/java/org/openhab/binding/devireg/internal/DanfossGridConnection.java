package org.openhab.binding.devireg.internal;

import java.io.IOException;

import javax.xml.bind.DatatypeConverter;

import org.eclipse.jdt.annotation.NonNull;
import org.opensdg.OSDGConnection;
import org.opensdg.OSDGResult;
import org.opensdg.OSDGState;
import org.opensdg.OpenSDG;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DanfossGridConnection extends OSDGConnection {
    private static final Logger logger = LoggerFactory.getLogger(DanfossGridConnection.class);
    private static DanfossGridConnection g_Conn;
    private static int numUsers = 0;

    public synchronized static DanfossGridConnection get(@NonNull ConfigurationAdmin confAdmin) {
        if (g_Conn == null) {
            g_Conn = new DanfossGridConnection();
            g_Conn.SetBlockingMode(true);

            DeviRegBindingConfig config = DeviRegBindingConfig.get();
            byte[] privateKey = config.getPrivateKey();
            boolean updateConfig = false;

            if (privateKey == null) {
                privateKey = OpenSDG.CreatePrivateKey();
                config.privateKey = DatatypeConverter.printHexBinary(privateKey);
                updateConfig = true;
            }

            // TODO: Library API will change, keys will belong to a connection
            OpenSDG.SetPrivateKey(privateKey);
            String publicKey = DatatypeConverter.printHexBinary(OpenSDG.GetMyPeerId());

            if (publicKey != config.publicKey) {
                config.publicKey = publicKey;
                updateConfig = true;
            }

            if (updateConfig) {
                try {
                    confAdmin.getConfiguration("binding.devireg", null).update(config.asDictionary());
                } catch (IOException e) {
                    logger.error("Failed to update binding config: " + e.getLocalizedMessage());
                }
            }
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
