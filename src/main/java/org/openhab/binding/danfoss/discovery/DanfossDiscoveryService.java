package org.openhab.binding.danfoss.discovery;

import java.util.Collections;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.DatatypeConverter;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openhab.binding.danfoss.internal.DanfossBindingConfig;
import org.openhab.binding.danfoss.internal.DanfossBindingConstants;
import org.openhab.binding.danfoss.internal.DanfossGridConnection;
import org.openhab.binding.danfoss.internal.DeviRegConfiguration;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.io.rest.JSONResponse;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.opensdg.OSDGConnection;
import org.opensdg.OSDGResult;
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
                    JSONObject request = new JSONObject();

                    // Request the data from the phone. chunkedMessage is important
                    // because if the data is too long, it wouldn't fit into fixed length
                    // buffer (approx. 1536 bytes) of phone's mdglib version. See comments
                    // in DeviSmartConfigConnection for more insight on this.
                    request.put("phoneName", userName);
                    request.put("phonePublicKey", DatatypeConverter.printHexBinary(grid.GetMyPeerId()));
                    request.put("chunkedMessage", true);

                    cfg.Send(request.toString().getBytes());
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

        JSONObject parsedConfig = new JSONObject(configJSON);
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
