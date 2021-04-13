package org.openhab.binding.danfoss.discovery;

import java.io.DataInputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.DatatypeConverter;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.io.rest.JSONResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openhab.binding.danfoss.internal.DanfossBindingConfig;
import org.openhab.binding.danfoss.internal.DanfossBindingConstants;
import org.openhab.binding.danfoss.internal.GridConnectionKeeper;
import org.openhab.binding.danfoss.internal.DeviRegConfiguration;
import org.opensdg.java.GridConnection;
import org.opensdg.java.PairingConnection;
import org.opensdg.java.PeerConnection;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public DanfossDiscoveryService() {
        super(Collections.singleton(DanfossBindingConstants.THING_TYPE_DEVIREG_SMART), 600, true);
    }

    @Override
    protected void startScan() {
        logger.error("Manual scan without parameters is not supported");
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

        JSONObject request = new JSONObject();

        // Request the data from the phone. chunkedMessage is important
        // because if the data is too long, it wouldn't fit into fixed length
        // buffer (approx. 1536 bytes) of phone's mdglib version. See comments
        // in DeviSmartConfigConnection for more insight on this.
        request.put("phoneName", userName);
        request.put("phonePublicKey", DatatypeConverter.printHexBinary(grid.getMyPeerId()));
        request.put("chunkedMessage", true);

        int dataSize = 0;
        int offset = 0;
        byte[] data = null;

        try {
            cfg.sendData(request.toString().getBytes());

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
        }

        /*
         * Configuration description is a JSON of the following self-explanatory format.
         * @formatter:off
         * Example from DeviSmart:
         * {
         *   "houseName":"My Flat",
         *   "houseEditUsers":false,
         *   "rooms":[
         *      {
         *        "roomName":"Living room",
         *        "peerId":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
         *        "zone":"Living",
         *        "sortOrder":0
         *      },
         *      {
         *        "roomName":"Kitchen",
         *        "peerId":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
         *        "zone":"None",
         *        "sortOrder":1
         *      }
         *   ]
         * }
         * Example from Icon:
         * {
         *   "houseName":"MyHouse",
         *   "housePeerId":" xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx ",
         *   "houseEditUsers":false
         * }
         * @formatter:on
         * "houseEditUsers", "zone" and "sortOrder" are used by the smartphone app only. Thermostats
         * are not aware of them.
         */

        JSONObject parsedConfig = new JSONObject(new String(data));
        String houseName = parsedConfig.getString("houseName");
        int thingCount = 0;

        logger.debug("Received house: {}", houseName);

        if (parsedConfig.has("rooms")) {
            JSONArray rooms = parsedConfig.getJSONArray("rooms");

            thingCount = rooms.length();
            for (int i = 0; i < thingCount; i++) {
                JSONObject room = rooms.getJSONObject(i);
                String roomName = room.getString("roomName");
                String peerId = room.getString("peerId");

                logger.debug("Received DeviSmart thing: {} {}", peerId, roomName);

                addThing(DanfossBindingConstants.THING_TYPE_DEVIREG_SMART, peerId,
                        "DeviReg Smart (" + houseName + " / " + roomName + ")");
            }
        }

        if (parsedConfig.has("housePeerId")) {
            String peerId = parsedConfig.getString("housePeerId");

            logger.debug("Received IconWifi thing: " + peerId);

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
