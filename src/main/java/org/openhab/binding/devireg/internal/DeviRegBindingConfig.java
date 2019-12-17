package org.openhab.binding.devireg.internal;

import java.util.Dictionary;
import java.util.Hashtable;
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

    public Dictionary<String, Object> asDictionary() {
        Dictionary<String, Object> data = new Hashtable<String, Object>();

        data.put("privateKey", privateKey);
        data.put("publicKey", publicKey);
        return data;
    }
}
