package org.openhab.binding.devireg.internal;

import static org.openhab.binding.devireg.internal.DeviRegBindingConstants.*;
import static org.opensdg.protocol.Icon.MsgClass.ROOM_FIRST;
import static org.opensdg.protocol.Icon.MsgCode.*;

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.opensdg.protocol.DeviSmart.Packet;
import org.opensdg.protocol.Icon.RoomMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IconRoomHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(IconRoomHandler.class);
    private int roomNumber;
    private PeerConnectionHandler connHandler;

    public IconRoomHandler(Thing thing) {
        super(thing);
        // TODO Auto-generated constructor stub
    }

    public int getNumber() {
        return roomNumber;
    }

    public void setConnectionHandler(PeerConnectionHandler h) {
        connHandler = h;
    }

    public void setConfigError(String reason) {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, reason);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String ch = channelUID.getId();

        logger.trace("Sending {} = {}", ch, command);

        switch (ch) {
            case CHANNEL_SETPOINT_COMFORT:
                setTemperature(ROOM_SETPOINTATHOME, command);
                break;
            case CHANNEL_SETPOINT_ECONOMY:
                setTemperature(ROOM_SETPOINTAWAY, command);
                break;
            case CHANNEL_SETPOINT_ASLEEP:
                setTemperature(ROOM_SETPOINTASLEEP, command);
                break;
            case CHANNEL_SETPOINT_MIN_FLOOR:
                setTemperature(ROOM_FLOORTEMPERATUREMINIMUM, command);
                break;
            case CHANNEL_SETPOINT_MAX_FLOOR:
                setTemperature(ROOM_FLOORTEMPERATUREMAXIMUM, command);
                break;
            // Read-only channels may send refreshType command
            case CHANNEL_TEMPERATURE_FLOOR:
                sendRefresh(ROOM_FLOORTEMPERATURE, command);
                break;
            case CHANNEL_TEMPERATURE_ROOM:
                sendRefresh(ROOM_ROOMTEMPERATURE, command);
                break;
        }
    }

    public void setTemperature(int msgCode, Command command) {
        if (connHandler != null) {
            connHandler.setTemperature(ROOM_FIRST + roomNumber, msgCode, command);
        }
    }

    public void sendRefresh(int msgCode, Command command) {
        if (connHandler != null) {
            connHandler.sendRefresh(ROOM_FIRST + roomNumber, msgCode, command);
        }
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
                reportTemperature(CHANNEL_SETPOINT_ASLEEP, pkt.getDecimal());
                break;
            case ROOM_SETPOINTAWAY:
                reportTemperature(CHANNEL_SETPOINT_ECONOMY, pkt.getDecimal());
                break;
            case ROOM_FLOORTEMPERATUREMINIMUM:
                reportTemperature(CHANNEL_SETPOINT_MIN_FLOOR, pkt.getDecimal());
                break;
            case ROOM_FLOORTEMPERATUREMAXIMUM:
                reportTemperature(CHANNEL_SETPOINT_MAX_FLOOR, pkt.getDecimal());
                break;
            case ROOM_BATTERYINDICATIONPERCENT:
                reportDecimal(CHANNEL_BATTERY, pkt.getByte());
                break;
            case ROOM_ROOMMODE:
                reportControlState(pkt.getByte());
                break;
            case ROOMNAME:
                updateProperty("roomName", pkt.getString());
                break;
        }
    }

    @SuppressWarnings("null")
    private void reportTemperature(String ch, double temp) {
        logger.trace("Received {} = {}", ch, temp);
        updateState(ch, new QuantityType<Temperature>(new DecimalType(temp), SIUnits.CELSIUS));
    }

    private void reportDecimal(String ch, long value) {
        logger.trace("Received {} = {}", ch, value);
        updateState(ch, new DecimalType(value));
    }

    private void reportControlState(byte info) {
        final String[] CONTROL_STATES = { "HOME", "AWAY", "ASLEEP", "FATAL" };
        String state;

        if (info >= RoomMode.AtHome && info <= RoomMode.Fatal) {
            state = CONTROL_STATES[info];
        } else {
            state = "";
        }

        logger.trace("Received {} = {}", CHANNEL_CONTROL_STATE, state);
        updateState(CHANNEL_CONTROL_STATE, StringType.valueOf(state));
    }
}
