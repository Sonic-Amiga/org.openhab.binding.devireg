package org.openhab.binding.devireg.internal;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Semaphore;

import org.opensdg.OSDGConnection;
import org.opensdg.OSDGResult;
import org.opensdg.OSDGState;

public class DeviSmartConfigConnection extends OSDGConnection {

    private int dataSize = 0;
    private String json = "";
    private boolean received = false;
    private Semaphore done = new Semaphore(0, false);

    @Override
    protected OSDGResult onDataReceived(byte[] data) {
        int offset = 0;
        int size = data.length;

        if (size > 8) {
            ByteBuffer header = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

            if (header.getInt() == 0) {
                dataSize = header.getInt();
                offset = 8;
                size -= 8;
            }
        }

        if (dataSize == 0) {
            dataSize = size;
        }

        json += new String(data, offset, size);
        dataSize -= size;

        if (dataSize <= 0) {
            received = true;
            done.release();
        }

        return OSDGResult.NO_ERROR;
    }

    @Override
    protected void onStatusChanged(OSDGState state) {
        if ((state != OSDGState.CONNECTED) && (!received)) {
            done.release();
        }
    }

    public String Receive() {
        done.acquireUninterruptibly();
        return received ? json : null;
    }
}
