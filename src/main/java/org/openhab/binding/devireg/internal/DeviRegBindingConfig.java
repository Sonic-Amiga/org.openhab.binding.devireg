package org.openhab.binding.devireg.internal;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeviRegBindingConfig {
    private static final Logger logger = LoggerFactory.getLogger(DeviRegBindingConfig.class);
    private static DeviRegBindingConfig g_Config = new DeviRegBindingConfig();

    public String privateKey;
    public String publicKey;

    public static DeviRegBindingConfig get() {
        return g_Config;
    }

    public void update(@NonNull DeviRegBindingConfig newConfig) {
        privateKey = newConfig.privateKey;
        publicKey = newConfig.publicKey;
    }

    public static void update(@NonNull Map<String, Object> config) {
        g_Config.update(new Configuration(config).as(DeviRegBindingConfig.class));
    }

    public void Save(ConfigurationAdmin confAdmin) {
        Dictionary<String, Object> data = new Hashtable<String, Object>();

        data.put("privateKey", privateKey);
        data.put("publicKey", publicKey);

        try {
            confAdmin.getConfiguration("binding.devireg", null).update(data);
        } catch (IOException e) {
            logger.error("Failed to update binding config: " + e.getLocalizedMessage());
        }
    }
}
