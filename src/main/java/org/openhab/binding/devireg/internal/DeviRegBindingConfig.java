package org.openhab.binding.devireg.internal;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.Configuration;

public class DeviRegBindingConfig {
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
}
