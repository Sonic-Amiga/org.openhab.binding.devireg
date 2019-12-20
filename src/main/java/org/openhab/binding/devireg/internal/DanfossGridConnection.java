package org.openhab.binding.devireg.internal;

import javax.xml.bind.DatatypeConverter;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
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
            g_Conn.SetPrivateKey(confAdmin, DeviRegBindingConfig.get());
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

    private void SetPrivateKey(ConfigurationAdmin confAdmin, DeviRegBindingConfig config) {
        byte[] privateKey = SDGUtils.ParseKey(config.privateKey);
        boolean updateConfig = false;

        if (privateKey == null) {
            privateKey = OpenSDG.CreatePrivateKey();
            config.privateKey = DatatypeConverter.printHexBinary(privateKey);
            logger.debug("Generated new private key: " + config.privateKey);
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
            config.Save(confAdmin);
        }
    }

    public static synchronized void UpdatePrivateKey(ConfigurationAdmin admin, @Nullable String newKey) {
        DeviRegBindingConfig config = DeviRegBindingConfig.get();

        if (newKey != null) {
            if (newKey.equals(config.privateKey)) {
                return;
            }

            if (!newKey.isEmpty()) {
                // Validate the new key and revert back to the old one if validation fails
                // It is rather dangerous to inadvertently damage it, you'll lose all
                // your thermostats and probably have to set everything up from scratch.
                if (SDGUtils.ParseKey(newKey) == null) {
                    config.Save(admin);
                    return;
                }
            }
        } else if (config.privateKey == null) {
            return;
        }

        logger.warn("Private key manually changed to: " + newKey);
        config.privateKey = newKey;

        if (g_Conn != null) {
            g_Conn.Close(); // Will reconnect on demand
            g_Conn.SetPrivateKey(admin, config);
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
