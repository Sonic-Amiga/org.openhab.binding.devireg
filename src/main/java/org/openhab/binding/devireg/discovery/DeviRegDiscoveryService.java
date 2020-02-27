package org.openhab.binding.devireg.discovery;

import java.util.Collections;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.DatatypeConverter;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.io.rest.JSONResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openhab.binding.devireg.internal.DanfossGridConnection;
import org.openhab.binding.devireg.internal.DeviRegBindingConfig;
import org.openhab.binding.devireg.internal.DeviRegBindingConstants;
import org.openhab.binding.devireg.internal.DeviRegConfiguration;
import org.opensdg.OSDGConnection;
import org.opensdg.OSDGResult;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = DiscoveryService.class)
public class DeviRegDiscoveryService extends AbstractDiscoveryService {

    private static DeviRegDiscoveryService instance;

    public static DeviRegDiscoveryService get() {
        return instance;
    }

    private Logger logger = LoggerFactory.getLogger(DeviRegDiscoveryService.class);

    public DeviRegDiscoveryService() {
        super(Collections.singleton(DeviRegBindingConstants.THING_TYPE_DEVIREG_SMART), 600, true);
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
        String userName = DeviRegBindingConfig.get().userName;

        if (userName == null || userName.isEmpty()) {
            return JSONResponse.createErrorResponse(Status.INTERNAL_SERVER_ERROR,
                    "Username is not set in binding configuration");
        }

        String configJSON = null;
        Status errorCode = Status.INTERNAL_SERVER_ERROR;
        String errorStr = null;

        DanfossGridConnection.AddUser();
        OSDGConnection grid = DanfossGridConnection.get();

        if (grid != null) {
            OSDGConnection pairing = new OSDGConnection();
            pairing.SetBlockingMode(true);

            OSDGResult r = pairing.PairRemote(grid, otp);

            switch (r) {
                case NO_ERROR:
                    logger.debug("Pairing successful");

                    // Our peer is the sending phone. Once we know the ID, we can establish data connection
                    byte[] phoneId = pairing.getPeerId();
                    DeviSmartConfigConnection cfg = new DeviSmartConfigConnection();

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
        } else {
            errorStr = "Failed to connect to Danfoss grid";
        }

        DanfossGridConnection.RemoveUser();

        if (errorStr != null) {
            return JSONResponse.createErrorResponse(errorCode, errorStr);
        }

        /*
         * Configuration description is a JSON of the following self-explanatory format:
         * @formatter:off
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
         * @formatter:on
         * "houseEditUsers", "zone" and "sortOrder" are used by the smartphone app only. Thermostats
         * are not aware of them.
         */

        JSONObject parsedConfig = new JSONObject(configJSON);
        String houseName = parsedConfig.getString("houseName");
        JSONArray rooms = parsedConfig.getJSONArray("rooms");

        logger.debug("Received house: " + houseName);

        for (int i = 0; i < rooms.length(); i++) {
            JSONObject room = rooms.getJSONObject(i);
            String roomName = room.getString("roomName");
            String peerId = room.getString("peerId");

            logger.debug("Received peer: " + peerId + " " + roomName);

            ThingUID thingUID = new ThingUID(DeviRegBindingConstants.THING_TYPE_DEVIREG_SMART, peerId);
            DiscoveryResult result = DiscoveryResultBuilder.create(thingUID)
                    .withProperty(DeviRegConfiguration.PEER_ID, peerId)
                    .withLabel("DeviReg Smart (" + houseName + " / " + roomName + ")").build();

            thingDiscovered(result);

        }

        return Response.ok("Received " + rooms.length() + " things, check your Inbox").build();
    }
}
