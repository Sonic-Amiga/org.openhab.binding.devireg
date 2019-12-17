package org.openhab.binding.devireg.internal;

import javax.xml.bind.DatatypeConverter;

public class SDGUtils {
    public static byte[] ParseKey(String keyStr) {
        if (keyStr == null) {
            return null;
        }

        byte[] key = DatatypeConverter.parseHexBinary(keyStr);
        return key.length == 32 ? key : null;
    }
}
