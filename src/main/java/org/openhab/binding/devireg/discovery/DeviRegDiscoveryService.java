package org.openhab.binding.devireg.discovery;

import java.util.Collections;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.DatatypeConverter;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.io.rest.JSONResponse;
import org.json.JSONObject;
import org.openhab.binding.devireg.internal.DanfossGridConnection;
import org.openhab.binding.devireg.internal.DeviRegBindingConfig;
import org.openhab.binding.devireg.internal.DeviRegBindingConstants;
import org.openhab.binding.devireg.internal.DeviSmartConfigConnection;
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

                    byte[] phoneId = pairing.getPeerId();

                    // Now we know phone's peer ID so we can establish data connection
                    DeviSmartConfigConnection cfg = new DeviSmartConfigConnection();
                    cfg.SetBlockingMode(true);

                    if (cfg.ConnectToRemote(grid, phoneId, "dominion-config-1.0") == OSDGResult.NO_ERROR) {
                        JSONObject request = new JSONObject();

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

        // Temporarily dump the JSON to the output
        return Response.ok(configJSON).build();
    }
}
