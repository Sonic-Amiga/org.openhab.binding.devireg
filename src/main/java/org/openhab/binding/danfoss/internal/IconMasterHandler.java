package org.openhab.binding.danfoss.internal;

import static org.openhab.binding.danfoss.internal.DanfossBindingConstants.*;
import static org.openhab.binding.danfoss.internal.protocol.Icon.MsgClass.*;
import static org.openhab.binding.danfoss.internal.protocol.Icon.MsgCode.*;

import java.text.DateFormat;
import java.util.Hashtable;
import java.util.concurrent.ScheduledExecutorService;

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.danfoss.discovery.IconRoomDiscoveryService;
import org.openhab.binding.danfoss.internal.protocol.Dominion;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IconMasterHandler extends BaseBridgeHandler implements ISDGPeerHandler {

    private final Logger logger = LoggerFactory.getLogger(IconMasterHandler.class);
    private PeerConnectionHandler connHandler = new PeerConnectionHandler(this);
    private IconRoomDiscoveryService discoveryService;
    private ServiceRegistration<?> discoveryReg;
    private Dominion.@Nullable Version firmwareVer;
    private int firmwareBuild = -1;

    private IconRoomHandler[] rooms = new IconRoomHandler[ICON_MAX_ROOMS];

    public IconMasterHandler(Bridge bridge, BundleContext bundleContext) {
        super(bridge);
        discoveryService = new IconRoomDiscoveryService(this);
        discoveryService.activate();
        discoveryReg = bundleContext.registerService(DiscoveryService.class.getName(), discoveryService,
                new Hashtable<>());
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String ch = channelUID.getId();

        logger.trace("Sending {} = {}", ch, command);

        switch (ch) {
            case CHANNEL_SETPOINT_AWAY:
                connHandler.setTemperature(ALL_ROOMS, VACATION_SETPOINT, command);
                break;
            case CHANNEL_SETPOINT_ANTIFREEZE:
                connHandler.setTemperature(ALL_ROOMS, PAUSE_SETPOINT, command);
                break;
        }
    }

    @Override
    public void initialize() {
        DeviRegConfiguration config = getConfigAs(DeviRegConfiguration.class);

        connHandler.initialize(config.peerId);
    }

    @Override
    public void dispose() {
        if (discoveryReg != null) {
            discoveryReg.unregister();
            if (discoveryService != null) {
                discoveryService.deactivate();
                discoveryService = null;
            }
        }
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
    public void handlePacket(Dominion.@NonNull Packet pkt) {
        int msgClass = pkt.getMsgClass();

        if (msgClass >= ROOM_FIRST && msgClass <= ROOM_LAST) {
            IconRoomHandler room = rooms[msgClass - ROOM_FIRST];

            if (room != null) {
                room.handlePacket(pkt);
            } else {
                discoveryService.handlePacket(pkt);
            }
        } else {
            switch (pkt.getMsgCode()) {
                case VACATION_SETPOINT:
                    reportTemperature(CHANNEL_SETPOINT_AWAY, pkt.getDecimal());
                    break;
                case PAUSE_SETPOINT:
                    reportTemperature(CHANNEL_SETPOINT_ANTIFREEZE, pkt.getDecimal());
                    break;
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

    private void reportTemperature(String ch, double temp) {
        logger.trace("Received {} = {}", ch, temp);
        updateState(ch, new QuantityType<@NonNull Temperature>(new DecimalType(temp), SIUnits.CELSIUS));
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
        // Need to request something small. Let's use VACATION_SETPOINT.
        connHandler.SendPacket(new Dominion.Packet(ALL_ROOMS, VACATION_SETPOINT));
    }

    public void scanRooms() {
        // Request names for all the rooms
        for (int msgClass = ROOM_FIRST; msgClass <= ROOM_LAST; msgClass++) {
            connHandler.SendPacket(new Dominion.Packet(msgClass, ROOMNAME));
        }
    }

    @Override
    public void childHandlerInitialized(ThingHandler handler, Thing thing) {
        super.childHandlerInitialized(handler, thing);

        if (handler instanceof IconRoomHandler) {
            IconRoomHandler room = (IconRoomHandler) handler;
            int roomId = room.getNumber();

            if (rooms[roomId] != null) {
                logger.error("Room number {} is already in use", roomId);
                room.setConfigError("Room number is already in use");
            } else {
                logger.trace("Room {} initialized", roomId);
                room.setConnectionHandler(connHandler);
                rooms[roomId] = room;
            }
        }
    }

    @Override
    public void childHandlerDisposed(ThingHandler handler, Thing thing) {
        if (handler instanceof IconRoomHandler) {
            IconRoomHandler room = (IconRoomHandler) handler;
            int roomId = room.getNumber();

            logger.trace("Room {} disposed", roomId);
            rooms[roomId] = null;
        }

        super.childHandlerDisposed(handler, thing);
    }
}
