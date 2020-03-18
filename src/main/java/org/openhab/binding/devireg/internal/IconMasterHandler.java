package org.openhab.binding.devireg.internal;

import static org.openhab.binding.devireg.internal.DeviRegBindingConstants.ICON_MAX_ROOMS;
import static org.opensdg.protocol.Icon.MsgClass.*;
import static org.opensdg.protocol.Icon.MsgCode.*;

import java.text.DateFormat;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.opensdg.protocol.DeviSmart;
import org.opensdg.protocol.DeviSmart.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IconMasterHandler extends BaseBridgeHandler implements ISDGPeerHandler {

    private final Logger logger = LoggerFactory.getLogger(DeviRegHandler.class);
    private PeerConnectionHandler connHandler = new PeerConnectionHandler(this);
    private DeviSmart.@Nullable Version firmwareVer;
    private int firmwareBuild = -1;

    private IconRoomHandler[] rooms = new IconRoomHandler[ICON_MAX_ROOMS];

    public IconMasterHandler(Bridge bridge) {
        super(bridge);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // TODO Auto-generated method stub

    }

    @Override
    public void initialize() {
        DeviRegConfiguration config = getConfigAs(DeviRegConfiguration.class);

        connHandler.initialize(config.peerId);
    }

    @Override
    public void dispose() {
        connHandler.dispose();
    }

    @Override
    public void reportStatus(@NonNull ThingStatus status, @NonNull ThingStatusDetail statusDetail, String description) {
        updateStatus(status, statusDetail, description);
    }

    @Override
    public void reportStatus(@NonNull ThingStatus status) {
        updateStatus(status);
    }

    @Override
    public void handlePacket(@NonNull Packet pkt) {
        int msgClass = pkt.getMsgClass();

        if (msgClass >= ROOM_FIRST && msgClass <= ROOM_LAST) {
            IconRoomHandler room = rooms[msgClass - ROOM_FIRST];

            if (room != null) {
                room.handlePacket(pkt);
            }
        } else {
            switch (pkt.getMsgCode()) {
                case GLOBAL_HARDWAREREVISION:
                    updateProperty(Thing.PROPERTY_HARDWARE_VERSION, pkt.getVersion().toString());
                    break;
                case GLOBAL_SOFTWAREREVISION:
                    firmwareVer = pkt.getVersion();
                    reportFirmware();
                    break;
                case GLOBAL_SOFTWAREBUILDREVISION:
                    firmwareBuild = Short.toUnsignedInt(pkt.getShort());
                    reportFirmware();
                    break;
                case GLOBAL_SERIALNUMBER:
                    updateProperty(Thing.PROPERTY_SERIAL_NUMBER, String.valueOf(pkt.getInt()));
                    break;
                case GLOBAL_PRODUCTIONDATE:
                    updateProperty("productionDate", DateFormat.getDateTimeInstance().format(pkt.getDate(0)));
                    break;
                case MDG_CONNECTION_COUNT:
                    updateProperty("connectionCount", String.valueOf(pkt.getByte()));
                    break;
            }
        }
    }

    private void reportFirmware() {
        if (firmwareVer != null && firmwareBuild != -1) {
            updateProperty(Thing.PROPERTY_FIRMWARE_VERSION,
                    firmwareVer.toString() + "." + String.valueOf(firmwareBuild));
        }
    }

    @Override
    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    @Override
    public void ping() {
        // TODO Auto-generated method stub
    }

    @Override
    public void childHandlerInitialized(ThingHandler handler, Thing thing) {
        super.childHandlerInitialized(handler, thing);

        if (handler instanceof IconRoomHandler) {
            IconRoomHandler room = (IconRoomHandler) handler;
            int roomId = room.getNumber();

            logger.trace("Room " + roomId + " initialized");
            rooms[roomId] = room;
        }
    }

    @Override
    public void childHandlerDisposed(ThingHandler handler, Thing thing) {
        if (handler instanceof IconRoomHandler) {
            IconRoomHandler room = (IconRoomHandler) handler;
            int roomId = room.getNumber();

            logger.trace("Room " + roomId + " disposed");
            rooms[roomId] = null;
        }

        super.childHandlerDisposed(handler, thing);
    }
}
