package org.openhab.binding.devireg.internal;

import static org.openhab.binding.devireg.internal.DeviRegBindingConstants.*;
import static org.opensdg.protocol.Icon.MsgCode.*;

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.opensdg.protocol.DeviSmart.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IconRoomHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(IconRoomHandler.class);
    private int roomNumber;

    public IconRoomHandler(Thing thing) {
        super(thing);
        // TODO Auto-generated constructor stub
    }

    public int getNumber() {
        return roomNumber;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // TODO Auto-generated method stub

    }

    @Override
    public void initialize() {
        IconRoomConfiguration config = getConfigAs(IconRoomConfiguration.class);

        roomNumber = config.roomNumber;

        logger.trace("Initializing room " + roomNumber);
        updateStatus(ThingStatus.UNKNOWN);
    }

    public void handlePacket(@NonNull Packet pkt) {
        switch (pkt.getMsgCode()) {
            case ROOM_FLOORTEMPERATURE:
                reportTemperature(CHANNEL_TEMPERATURE_FLOOR, pkt.getDecimal());
                break;
            case ROOM_ROOMTEMPERATURE:
                reportTemperature(CHANNEL_TEMPERATURE_ROOM, pkt.getDecimal());
                break;
            case ROOM_SETPOINTATHOME:
                reportTemperature(CHANNEL_SETPOINT_COMFORT, pkt.getDecimal());
                break;
            case ROOM_SETPOINTASLEEP:
                reportTemperature(CHANNEL_SETPOINT_ECONOMY, pkt.getDecimal());
                break;
            case ROOM_SETPOINTAWAY:
                reportTemperature(CHANNEL_SETPOINT_AWAY, pkt.getDecimal());
                break;
            case ROOM_FLOORTEMPERATUREMINIMUM:
                reportTemperature(CHANNEL_SETPOINT_MIN_FLOOR, pkt.getDecimal());
                break;
            case ROOM_FLOORTEMPERATUREMAXIMUM:
                reportTemperature(CHANNEL_SETPOINT_MAX_FLOOR, pkt.getDecimal());
                break;
            /*
             * case ROOM_ROOMMODE:
             * reportControlInfo(pkt.getByte());
             * break;
             */
        }
    }

    private void reportTemperature(String ch, double temp) {
        logger.trace("Received {} = {}", ch, temp);
        updateState(ch, new QuantityType<Temperature>(new DecimalType(temp), SIUnits.CELSIUS));
    }
}
