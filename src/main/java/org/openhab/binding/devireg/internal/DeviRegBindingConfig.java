package org.openhab.binding.devireg.internal;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.Configuration;
import org.opensdg.OpenSDG;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeviRegBindingConfig {
    private static final Logger logger = LoggerFactory.getLogger(DeviRegBindingConfig.class);
    private static DeviRegBindingConfig g_Config = new DeviRegBindingConfig();

    public String privateKey;
    public String publicKey;
    public String userName;

    public static DeviRegBindingConfig get() {
        return g_Config;
    }

    private void update(DeviRegBindingConfig newConfig) {
        String newKey = newConfig.privateKey;
        byte[] newPrivkey;

        userName = newConfig.userName;

        if (newKey == null || newKey.isEmpty()) {
            newPrivkey = OpenSDG.CreatePrivateKey();
            newKey = DatatypeConverter.printHexBinary(newPrivkey);

            logger.debug("Created new private key: " + newKey);
        } else if (newKey.equals(privateKey)) {
            return;
        } else {
            // Validate the new key and revert back to the old one if validation fails
            // It is rather dangerous to inadvertently damage it, you'll lose all
            // your thermostats and probably have to set everything up from scratch.
            newPrivkey = SDGUtils.ParseKey(newKey);

            if (newPrivkey == null) {
                logger.warn("Invalid private key configured: " + newKey + "; reverting back to old one");
                return;
            }

            logger.debug("Got private key from configuration: " + newKey);
        }

        privateKey = newKey;
        publicKey = DatatypeConverter.printHexBinary(OpenSDG.CalcPublicKey(newPrivkey));
        userName = newConfig.userName;
    }

    public static void update(@NonNull Map<String, Object> config, ConfigurationAdmin admin) {
        DeviRegBindingConfig newConfig = new Configuration(config).as(DeviRegBindingConfig.class);

        // Kludge for OpenHAB 2.4. Early development versions of this binding didn't have
        // this parameter. OpenHAB apparently cached parameter structure and doesn't present
        // the new option in binding config. Consequently, the field in DeviRegBindingConfig
        // object stays null.
        if (newConfig.userName == null) {
            newConfig.userName = "OpenHAB";
        }

        g_Config.update(newConfig);

        if (!(g_Config.privateKey.equals(newConfig.privateKey) && g_Config.publicKey.equals(newConfig.publicKey)
                && g_Config.userName.equals(newConfig.userName))) {
            // Some value has been updated by the validation, save the validated version
            g_Config.Save(admin);
        }
    }

    public void Save(ConfigurationAdmin confAdmin) {
        Dictionary<String, Object> data = new Hashtable<String, Object>();

        data.put("privateKey", privateKey);
        data.put("publicKey", publicKey);
        data.put("userName", userName);

        try {
            confAdmin.getConfiguration("binding.devireg", null).update(data);
        } catch (IOException e) {
            logger.error("Failed to update binding config: " + e.getLocalizedMessage());
        }
    }
}
