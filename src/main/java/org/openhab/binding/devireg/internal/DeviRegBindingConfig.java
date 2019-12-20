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

    public static void update(@NonNull Map<String, Object> config) {
        DeviRegBindingConfig newConfig = new Configuration(config).as(DeviRegBindingConfig.class);

        // We update instead of replace the configuration object, so that if the user updates the
        // configuration, the values are automatically available in all handlers. Because they all
        // share the same instance.
        g_Config.privateKey = newConfig.privateKey;
        g_Config.publicKey = newConfig.publicKey;
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
