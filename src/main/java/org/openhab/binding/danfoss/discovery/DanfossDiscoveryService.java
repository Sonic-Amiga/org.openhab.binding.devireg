package org.openhab.binding.danfoss.discovery;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.rmi.RemoteException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.openhab.binding.danfoss.internal.DanfossBindingConfig;
import org.openhab.binding.danfoss.internal.DanfossBindingConstants;
import org.openhab.binding.danfoss.internal.DeviRegConfiguration;
import org.openhab.binding.danfoss.internal.GridConnectionKeeper;
import org.openhab.binding.danfoss.internal.protocol.DominionConfiguration;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.io.rest.JSONResponse;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.opensdg.java.GridConnection;
import org.opensdg.java.PairingConnection;
import org.opensdg.java.PeerConnection;
import org.opensdg.java.SDG;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

@Component(service = DiscoveryService.class)
public class DanfossDiscoveryService extends AbstractDiscoveryService {

    private static class OKResponse {
        @SuppressWarnings("unused") // Used by JSONResponse for serialization
        public int thingCount;

        public OKResponse(int count) {
            thingCount = count;
        }
    }

    private static DanfossDiscoveryService instance;

    public static DanfossDiscoveryService get() {
        return instance;
    }

    private Logger logger = LoggerFactory.getLogger(DanfossDiscoveryService.class);

    private final Gson gson = new Gson();

    public DanfossDiscoveryService() {
        super(Collections.singleton(DanfossBindingConstants.THING_TYPE_DEVIREG_SMART), 600, true);
    }

    @Override
    protected void startScan() {
        // This does nothing as manual scan requires an OTP, which can't be provided
        // by OpenHAB's default functionality.
    }

    @Override
    protected void startBackgroundDiscovery() {
        logger.trace("startBackgroundDiscovery");
        instance = this;
    }

    @Override
    protected void stopBackgroundDiscovery() {
        logger.trace("stopBackgroundDiscovery");
        instance = null;
    }

    public Response receiveConfig(String otp) {
        logger.trace("Pairing with OTP: {}", otp);

        String userName = DanfossBindingConfig.get().userName;
        GridConnection grid;

        if (userName == null || userName.isEmpty()) {
            return JSONResponse.createErrorResponse(Status.INTERNAL_SERVER_ERROR,
                    "Username is not set in binding configuration");
        }

        GridConnectionKeeper.AddUser();

        try {
            grid = GridConnectionKeeper.getConnection();
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            GridConnectionKeeper.RemoveUser();
            return JSONResponse.createErrorResponse(Status.INTERNAL_SERVER_ERROR, e.getMessage());
        }

        byte[] myPeerId = grid.getMyPeerId();

        if (myPeerId == null) {
            // This is an impossible situation, but Eclipse thinks it knows better :(
            GridConnectionKeeper.RemoveUser();
            return JSONResponse.createErrorResponse(Status.INTERNAL_SERVER_ERROR, "PeerID is not set");
        }

        Status errorCode = Status.INTERNAL_SERVER_ERROR;
        String errorStr = null;

        PairingConnection pairing = new PairingConnection();

        try {
            pairing.pairWithRemote(grid, otp);
        } catch (RemoteException e) {
            return JSONResponse.createErrorResponse(Status.NOT_FOUND, "Connection refused by peer; likely wrong OTP");
        } catch (IOException | InterruptedException | ExecutionException | GeneralSecurityException
                | TimeoutException e) {
            return JSONResponse.createErrorResponse(Status.INTERNAL_SERVER_ERROR, "Pairing failed: " + e.toString());
        }

        byte[] phoneId = pairing.getPeerId();
        pairing.close();

        logger.debug("Pairing successful");

        // Our peer is the sending phone. Once we know the ID, we can establish data connection
        PeerConnection cfg = new PeerConnection();

        try {
            cfg.connectToRemote(grid, phoneId, "dominion-configuration-1.0");
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            return JSONResponse.createErrorResponse(Status.INTERNAL_SERVER_ERROR,
                    "Failed to connect to the sender: " + e.toString());
        }

        DominionConfiguration.Request request = new DominionConfiguration.Request(userName, SDG.bin2hex(myPeerId));

        int dataSize = 0;
        int offset = 0;
        byte[] data = null;

        try {
            cfg.sendData(gson.toJson(request).getBytes());

            do {
                DataInputStream chunk = new DataInputStream(cfg.receiveData());
                int chunkSize = chunk.available();

                if (chunkSize > 8) {
                    // In chunked mode the data will arrive in several packets.
                    // The first one will contain the header, specifying full data length.
                    // The header has integer 0 in the beginning so that it's easily distinguished
                    // from JSON plaintext
                    if (chunk.readInt() == 0) {
                        // Size is little-endian here
                        dataSize = Integer.reverseBytes(chunk.readInt());
                        logger.trace("Chunked mode; full size = {}", dataSize);
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
                    logger.trace("Raw mode; full size = {}", dataSize);
                    data = new byte[dataSize];
                }

                chunk.read(data, offset, chunkSize);
                offset += chunkSize;
            } while (offset < dataSize);
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            errorStr = "Failed to receive config: " + e.toString();
        }

        cfg.close();
        GridConnectionKeeper.RemoveUser();

        if (errorStr != null) {
            return JSONResponse.createErrorResponse(errorCode, errorStr);
        } else if (data == null) {
            // This is an impossible situation, but Eclipse forces us to have this check
            return JSONResponse.createErrorResponse(Status.INTERNAL_SERVER_ERROR, "Unknown error (data == null");
        }

        // If we use fromJson(String) or fromJson(java.util.reader), it will throw
        // "JSON not fully consumed" exception, because not all the reader's content has been
        // used up. We avoid that for backwards compatibility reasons because newer application
        // versions may add fields.
        JsonReader jsonReader = new JsonReader(new StringReader(new String(data)));
        DominionConfiguration.Response parsedConfig = gson.fromJson(jsonReader, DominionConfiguration.Response.class);
        String houseName = parsedConfig.houseName;
        int thingCount = 0;

        logger.debug("Received house: {}", houseName);

        if (parsedConfig.rooms != null) {
            thingCount = parsedConfig.rooms.length;
            for (DominionConfiguration.Room room : parsedConfig.rooms) {
                String roomName = room.roomName;
                String peerId = room.peerId;

                logger.debug("Received DeviSmart thing: {} {}", peerId, roomName);

                addThing(DanfossBindingConstants.THING_TYPE_DEVIREG_SMART, peerId,
                        "DeviReg Smart (" + houseName + " / " + roomName + ")");
            }
        }

        if (parsedConfig.housePeerId != null) {
            String peerId = parsedConfig.housePeerId;

            logger.debug("Received IconWifi thing: {}", peerId);

            thingCount = 1;
            addThing(DanfossBindingConstants.THING_TYPE_ICON_WIFI, peerId, "Danfoss Icon Wifi (" + houseName + ")");
        }

        OKResponse response = new OKResponse(thingCount);
        return JSONResponse.createResponse(Status.OK, response, "OK");
    }

    private void addThing(ThingTypeUID typeId, String peerId, String label) {
        ThingUID thingUID = new ThingUID(typeId, peerId);
        DiscoveryResult result = DiscoveryResultBuilder.create(thingUID)
                .withProperty(DeviRegConfiguration.PEER_ID, peerId).withLabel(label).build();

        thingDiscovered(result);
    }
}
