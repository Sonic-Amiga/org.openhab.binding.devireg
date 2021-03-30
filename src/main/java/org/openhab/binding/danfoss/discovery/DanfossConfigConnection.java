package org.openhab.binding.danfoss.discovery;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.opensdg.java.PeerConnection;

public class DanfossConfigConnection extends PeerConnection {

    public String Receive() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        int dataSize = 0;
        int offset = 0;
        byte[] data = null;

        do {
            DataInputStream chunk = new DataInputStream(receiveData());
            int chunkSize = chunk.available();

            if (chunkSize > 8) {
                // In chunked mode the data will arrive in several packets.
                // The first one will contain the header, specifying full data length.
                // The header has integer 0 in the beginning so that it's easily distinguished
                // from JSON plaintext
                if (chunk.readInt() == 0) {
                    // Size is little-endian here
                    dataSize = Integer.reverseBytes(chunk.readInt());
                    System.out.println("Chunked mode; full size = " + dataSize);
                    data = new byte[dataSize];
                    chunkSize -= 8; // We've consumed the header
                } else {
                    // No header, go back to the beginning
                    chunk.reset();
                }
            }

            if (dataSize == 0) {
                // If the first packet didn't contain the header, this is not
                // a chunked mode, so just use the complete length of this packet
                // and we're done
                dataSize = chunkSize;
                data = new byte[dataSize];
            }

            chunk.read(data, offset, chunkSize);
            offset += chunkSize;
        } while (offset < dataSize);

        return new String(data);
    }
}
