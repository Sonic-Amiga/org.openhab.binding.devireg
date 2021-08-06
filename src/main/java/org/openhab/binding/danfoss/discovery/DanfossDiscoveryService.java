package org.openhab.binding.danfoss.discovery;

import java.io.StringReader;
import java.util.Collections;

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
import org.openhab.binding.danfoss.internal.DanfossBindingConfig;
import org.openhab.binding.danfoss.internal.DanfossBindingConstants;
import org.openhab.binding.danfoss.internal.DanfossGridConnection;
import org.openhab.binding.danfoss.internal.DeviRegConfiguration;
import org.openhab.binding.danfoss.internal.protocol.DominionConfiguration;
import org.opensdg.OSDGConnection;
import org.opensdg.OSDGResult;
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
        OSDGConnection grid;

        if (userName == null || userName.isEmpty()) {
            return JSONResponse.createErrorResponse(Status.INTERNAL_SERVER_ERROR,
                    "Username is not set in binding configuration");
        }

        DanfossGridConnection.AddUser();

        try {
            grid = DanfossGridConnection.get();
        } catch (Exception e) {
            DanfossGridConnection.RemoveUser();
            return JSONResponse.createErrorResponse(Status.INTERNAL_SERVER_ERROR, e.getMessage());
        }

        String configJSON = null;
        Status errorCode = Status.INTERNAL_SERVER_ERROR;
        String errorStr = null;

        OSDGConnection pairing = new OSDGConnection();
        pairing.SetBlockingMode(true);

        OSDGResult r = pairing.PairRemote(grid, otp);

        switch (r) {
            case NO_ERROR:
                logger.debug("Pairing successful");

                // Our peer is the sending phone. Once we know the ID, we can establish data connection
                byte[] phoneId = pairing.getPeerId();
                DanfossConfigConnection cfg = new DanfossConfigConnection();

                cfg.SetBlockingMode(true);

                if (cfg.ConnectToRemote(grid, phoneId, "dominion-config-1.0") == OSDGResult.NO_ERROR) {
                    // Request the data from the phone. chunkedMessage is important
                    // because if the data is too long, it wouldn't fit into fixed length
                    // buffer (approx. 1536 bytes) of phone's mdglib version. See comments
                    // in DeviSmartConfigConnection for more insight on this.
                    DominionConfiguration.Request request = new DominionConfiguration.Request(userName,
                            DatatypeConverter.printHexBinary(grid.GetMyPeerId()));

                    cfg.Send(gson.toJson(request).getBytes());
                    configJSON = cfg.Receive();

                    if (configJSON == null) {
                        errorStr = "Failed to receive config: " + cfg.getLastResultStr();
                    }

                    cfg.Close();

                } else {
                    errorStr = "Failed to connect to the sender: " + cfg.getLastResultStr();
                }

                cfg.Dispose();
                break;

            case INVALID_PARAMETERS:
                errorStr = "Invalid OTP supplied";
                errorCode = Status.BAD_REQUEST;
                break;

            case CONNECTION_REFUSED:
                errorStr = "Connection refused by peer; likely wrong OTP";
                errorCode = Status.NOT_FOUND;
                break;

            default:
                errorStr = "Pairing failed: " + pairing.getLastResultStr();
                break;
        }

        pairing.Dispose();
        DanfossGridConnection.RemoveUser();

        if (errorStr != null) {
            return JSONResponse.createErrorResponse(errorCode, errorStr);
        }

        // If we use fromJson(String) or fromJson(java.util.reader), it will throw
        // "JSON not fully consumed" exception, because not all the reader's content has been
        // used up. We avoid that for backwards compatibility reasons because newer application
        // versions may add fields.
        JsonReader jsonReader = new JsonReader(new StringReader(configJSON));
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
