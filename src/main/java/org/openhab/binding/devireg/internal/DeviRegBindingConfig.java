package org.openhab.binding.devireg.internal;

import org.eclipse.jdt.annotation.NonNull;

public class DeviRegBindingConfig {
    public String privateKey;
    public String publicKey;

    public void update(@NonNull DeviRegBindingConfig newConfig) {
        privateKey = newConfig.privateKey;
        publicKey = newConfig.publicKey;
    }
}
