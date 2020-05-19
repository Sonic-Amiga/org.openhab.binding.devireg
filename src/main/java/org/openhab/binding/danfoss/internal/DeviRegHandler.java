/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.danfoss.internal;

import static org.openhab.binding.danfoss.internal.DanfossBindingConstants.*;
import static org.opensdg.protocol.DeviSmart.MsgClass.*;
import static org.opensdg.protocol.DeviSmart.MsgCode.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.concurrent.ScheduledExecutorService;

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.opensdg.protocol.DeviSmart;
import org.opensdg.protocol.DeviSmart.ControlMode;
import org.opensdg.protocol.DeviSmart.ControlState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DeviRegHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Pavel Fedin - Initial contribution
 */
@NonNullByDefault
public class DeviRegHandler extends BaseThingHandler implements ISDGPeerHandler {

    private final Logger logger = LoggerFactory.getLogger(DeviRegHandler.class);
    private PeerConnectionHandler connHandler = new PeerConnectionHandler(this);
    private byte currentMode = -1;
    private DeviSmart.@Nullable Version firmwareVer;
    private int firmwareBuild = -1;

    public DeviRegHandler(Thing thing) {
        super(thing);
    }

    @SuppressWarnings("null")
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String ch = channelUID.getId();

        logger.trace("Sending {} = {}", ch, command);

        switch (ch) {
            case CHANNEL_SETPOINT_WARNING:
                setTemperature(DOMINION_HEATING, HEATING_LOW_TEMPERATURE_WARNING, command);
                break;
            case CHANNEL_SETPOINT_COMFORT:
                setTemperature(DOMINION_SCHEDULER, SCHEDULER_SETPOINT_COMFORT, command);
                break;
            case CHANNEL_SETPOINT_ECONOMY:
                setTemperature(DOMINION_SCHEDULER, SCHEDULER_SETPOINT_ECONOMY, command);
                break;
            case CHANNEL_SETPOINT_MANUAL:
                setTemperature(DOMINION_SCHEDULER, SCHEDULER_SETPOINT_MANUAL, command);
                break;
            case CHANNEL_SETPOINT_AWAY:
                setTemperature(DOMINION_SCHEDULER, SCHEDULER_SETPOINT_AWAY, command);
                break;
            case CHANNEL_SETPOINT_ANTIFREEZE:
                setTemperature(DOMINION_SCHEDULER, SCHEDULER_SETPOINT_FROST_PROTECTION, command);
                break;
            case CHANNEL_SETPOINT_MIN_FLOOR:
                setTemperature(DOMINION_SCHEDULER, SCHEDULER_SETPOINT_FLOOR_COMFORT, command);
                break;
            case CHANNEL_SETPOINT_MIN_FLOOR_ENABLE:
                setSwitch(DOMINION_SCHEDULER, SCHEDULER_SETPOINT_FLOOR_COMFORT_ENABLED, command);
                break;
            case CHANNEL_SETPOINT_MAX_FLOOR:
                setTemperature(DOMINION_SCHEDULER, SCHEDULER_SETPOINT_MAX_FLOOR, command);
                break;
            case CHANNEL_CONTROL_MODE:
                setMode(command);
                break;
            case CHANNEL_WINDOW_DETECTION:
                setSwitch(DOMINION_SYSTEM, SYSTEM_WINDOW_OPEN, command);
                break;
            case CHANNEL_FORECAST:
                setSwitch(DOMINION_SYSTEM, SYSTEM_INFO_FORECAST_ENABLED, command);
                break;
            case CHANNEL_SCREEN_LOCK:
                setSwitch(DOMINION_SYSTEM, SYSTEM_UI_SAFETY_LOCK, command);
                break;
            case CHANNEL_BRIGHTNESS:
                setByte(DOMINION_SYSTEM, SYSTEM_UI_BRIGTHNESS, command);
                break;
            // Read-only channels may send refreshType command
            case CHANNEL_TEMPERATURE_FLOOR:
                sendRefresh(DOMINION_HEATING, HEATING_TEMPERATURE_FLOOR, command);
                break;
            case CHANNEL_TEMPERATURE_ROOM:
                sendRefresh(DOMINION_HEATING, HEATING_TEMPERATURE_ROOM, command);
                break;
            case CHANNEL_CONTROL_STATE:
                sendRefresh(DOMINION_SCHEDULER, SCHEDULER_CONTROL_INFO, command);
                break;
            case CHANNEL_WINDOW_OPEN:
                sendRefresh(DOMINION_SYSTEM, SYSTEM_INFO_WINDOW_OPEN_DETECTION, command);
                break;
        }
    }

    private void setTemperature(int msgClass, int msgCode, Command command) {
        double newTemperature;

        if (command instanceof DecimalType) {
            newTemperature = ((DecimalType) command).doubleValue();
        } else if (command instanceof QuantityType) {
            @SuppressWarnings("unchecked")
            QuantityType<Temperature> celsius = ((QuantityType<Temperature>) command).toUnit(SIUnits.CELSIUS);
            if (celsius == null) {
                return;
            }
            newTemperature = celsius.doubleValue();
        } else {
            sendRefresh(msgClass, msgCode, command);
            return;
        }

        connHandler.SendPacket(new DeviSmart.Packet(msgClass, msgCode, newTemperature));
    }

    private void setSwitch(int msgClass, int msgCode, Command command) {
        if (command instanceof OnOffType) {
            connHandler.SendPacket(new DeviSmart.Packet(msgClass, msgCode, command.equals(OnOffType.ON)));
        } else {
            sendRefresh(msgClass, msgCode, command);
        }
    }

    private void setByte(int msgClass, int msgCode, Command command) {
        if (command instanceof DecimalType) {
            connHandler.SendPacket(new DeviSmart.Packet(msgClass, msgCode, ((DecimalType) command).byteValue()));
        } else {
            sendRefresh(msgClass, msgCode, command);
        }
    }

    private void setMode(Command command) {
        if (command instanceof StringType) {
            try {
                String cmdString = command.toString();
                // We are going to send more than one packet, let's collect them and send
                // together.
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                // In "special" modes (Off, Pause, Vacation) the thermostat ignores
                // other mode commands; we first need to cancel this mode. We also
                // check if the same mode is requested and just bail out in such a case.
                switch (currentMode) {
                    case ControlState.Configuring:
                    case ControlState.Fatal:
                        return; // I think we cannot do much here

                    case ControlState.Vacation:
                        if (cmdString.equals(CONTROL_MODE_VACATION)) {
                            return;
                        }

                        // Original app resets both scheduled period and PLANNED flag, we do the same.
                        buffer.write(new DeviSmart.AwayPacket(null, null).getBuffer());
                        buffer.write(
                                new DeviSmart.Packet(DOMINION_SCHEDULER, SCHEDULER_AWAY_ISPLANNED, false).getBuffer());
                        break;

                    case ControlState.Pause:
                        if (cmdString.equals(CONTROL_MODE_PAUSE)) {
                            return;
                        }

                        buffer.write(new DeviSmart.Packet(DOMINION_SCHEDULER, SCHEDULER_CONTROL_MODE,
                                ControlMode.FROST_PROTECTION_OFF).getBuffer());
                        break;

                    case ControlState.Off:
                        if (cmdString.equals(CONTROL_MODE_OFF)) {
                            return;
                        }

                        buffer.write(new DeviSmart.Packet(DOMINION_SCHEDULER, SCHEDULER_CONTROL_MODE,
                                ControlMode.OFF_STATE_OFF).getBuffer());
                        break;

                    case ControlState.AtHomeOverride:
                        if (cmdString.equals(CONTROL_MODE_OVERRIDE)) {
                            return;
                        }

                        buffer.write(new DeviSmart.Packet(DOMINION_SCHEDULER, SCHEDULER_CONTROL_MODE,
                                ControlMode.TEMPORARY_HOME_OFF).getBuffer());
                        break;
                }

                switch (cmdString) {
                    case CONTROL_MODE_MANUAL:
                        buffer.write(new DeviSmart.Packet(DOMINION_SCHEDULER, SCHEDULER_CONTROL_MODE,
                                ControlMode.WEEKLY_SCHEDULE_OFF).getBuffer());
                        break;
                    case CONTROL_MODE_OVERRIDE:
                        buffer.write(new DeviSmart.Packet(DOMINION_SCHEDULER, SCHEDULER_CONTROL_MODE,
                                ControlMode.TEMPORARY_HOME_ON).getBuffer());
                        break;
                    case CONTROL_MODE_SCHEDULE:
                        buffer.write(new DeviSmart.Packet(DOMINION_SCHEDULER, SCHEDULER_CONTROL_MODE,
                                ControlMode.WEEKLY_SCHEDULE_ON).getBuffer());
                        break;
                    case CONTROL_MODE_VACATION:
                        // In order to enter vacation mode immediately we need to reset
                        // scheduled time period and set PLANNED to true.
                        buffer.write(new DeviSmart.AwayPacket(null, null).getBuffer());
                        buffer.write(
                                new DeviSmart.Packet(DOMINION_SCHEDULER, SCHEDULER_AWAY_ISPLANNED, true).getBuffer());
                        break;
                    case CONTROL_MODE_PAUSE:
                        buffer.write(new DeviSmart.Packet(DOMINION_SCHEDULER, SCHEDULER_CONTROL_MODE,
                                ControlMode.FROST_PROTECTION_ON).getBuffer());
                        break;
                    case CONTROL_MODE_OFF:
                        buffer.write(new DeviSmart.Packet(DOMINION_SCHEDULER, SCHEDULER_CONTROL_MODE,
                                ControlMode.OFF_STATE_ON).getBuffer());
                        break;
                }

                connHandler.Send(buffer.toByteArray());

            } catch (IOException e) {
                // We should never get here
                logger.error("Error building control mode packet(s): " + e.toString());
            }
        } else {
            sendRefresh(DOMINION_SCHEDULER, SCHEDULER_CONTROL_INFO, command);
        }
    }

    private void sendRefresh(int msgClass, int msgCode, Command command) {
        if (command instanceof RefreshType) {
            connHandler.SendPacket(new DeviSmart.Packet(msgClass, msgCode));
        }
    }

    @SuppressWarnings("null")
    @Override
    public void initialize() {
        DeviRegConfiguration config = getConfigAs(DeviRegConfiguration.class);

        connHandler.initialize(config.peerId);
    }

    @Override
    public void dispose() {
        connHandler.dispose();
    }

    private void reportTemperature(String ch, double temp) {
        logger.trace("Received {} = {}", ch, temp);
        updateState(ch, new QuantityType<Temperature>(new DecimalType(temp), SIUnits.CELSIUS));
    }

    private void reportSwitch(String ch, boolean on) {
        logger.trace("Received {} = {}", ch, on);
        updateState(ch, OnOffType.from(on));
    }

    private void reportDecimal(String ch, long value) {
        logger.trace("Received {} = {}", ch, value);
        updateState(ch, new DecimalType(value));
    }

    private void reportControlInfo(byte info) {
        String mode, state;

        currentMode = info;

        if (info >= ControlState.Configuring && info <= ControlState.AtHomeOverride) {
            mode = CONTROL_MODES[info];
            state = CONTROL_STATES[info];
        } else {
            mode = "";
            state = "";
        }

        logger.trace("Received {} = {}", CHANNEL_CONTROL_STATE, state);

        updateState(CHANNEL_CONTROL_MODE, StringType.valueOf(mode));
        updateState(CHANNEL_CONTROL_STATE, StringType.valueOf(state));
    }

    private void reportFirmware() {
        if (firmwareVer != null && firmwareBuild != -1) {
            updateProperty(Thing.PROPERTY_FIRMWARE_VERSION,
                    firmwareVer.toString() + "." + String.valueOf(firmwareBuild));
        }
    }

    @Override
    public void handlePacket(DeviSmart.Packet pkt) {
        switch (pkt.getMsgCode()) {
            case HEATING_TEMPERATURE_FLOOR:
                reportTemperature(CHANNEL_TEMPERATURE_FLOOR, pkt.getDecimal());
                break;
            case HEATING_TEMPERATURE_ROOM:
                reportTemperature(CHANNEL_TEMPERATURE_ROOM, pkt.getDecimal());
                break;
            case HEATING_LOW_TEMPERATURE_WARNING:
                reportTemperature(CHANNEL_SETPOINT_WARNING, pkt.getDecimal());
                break;
            case SCHEDULER_SETPOINT_COMFORT:
                reportTemperature(CHANNEL_SETPOINT_COMFORT, pkt.getDecimal());
                break;
            case SCHEDULER_SETPOINT_ECONOMY:
                reportTemperature(CHANNEL_SETPOINT_ECONOMY, pkt.getDecimal());
                break;
            case SCHEDULER_SETPOINT_MANUAL:
                reportTemperature(CHANNEL_SETPOINT_MANUAL, pkt.getDecimal());
                break;
            case SCHEDULER_SETPOINT_AWAY:
                reportTemperature(CHANNEL_SETPOINT_AWAY, pkt.getDecimal());
                break;
            case SCHEDULER_SETPOINT_FROST_PROTECTION:
                reportTemperature(CHANNEL_SETPOINT_ANTIFREEZE, pkt.getDecimal());
                break;
            case SCHEDULER_SETPOINT_FLOOR_COMFORT:
                reportTemperature(CHANNEL_SETPOINT_MIN_FLOOR, pkt.getDecimal());
                break;
            case SCHEDULER_SETPOINT_FLOOR_COMFORT_ENABLED:
                reportSwitch(CHANNEL_SETPOINT_MIN_FLOOR_ENABLE, pkt.getBoolean());
                break;
            case SCHEDULER_SETPOINT_MAX_FLOOR:
                reportTemperature(CHANNEL_SETPOINT_MAX_FLOOR, pkt.getDecimal());
                break;
            case SCHEDULER_CONTROL_INFO:
                reportControlInfo(pkt.getByte());
                break;
            case SYSTEM_WINDOW_OPEN:
                reportSwitch(CHANNEL_WINDOW_DETECTION, pkt.getBoolean());
                break;
            case SYSTEM_INFO_WINDOW_OPEN_DETECTION:
                reportSwitch(CHANNEL_WINDOW_OPEN, pkt.getBoolean());
                break;
            case SYSTEM_INFO_FORECAST_ENABLED:
                reportSwitch(CHANNEL_FORECAST, pkt.getBoolean());
                break;
            case SYSTEM_UI_SAFETY_LOCK:
                reportSwitch(CHANNEL_SCREEN_LOCK, pkt.getBoolean());
                break;
            case SYSTEM_UI_BRIGTHNESS:
                reportDecimal(CHANNEL_BRIGHTNESS, pkt.getByte());
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
            case WIFI_CONNECTED_STRENGTH:
                updateProperty("wifiStrength", String.valueOf(pkt.getShort()) + " db");
                break;
            case SYSTEM_RUNTIME_INFO_RELAY_COUNT:
                updateProperty("relayCount", String.valueOf(pkt.getInt()));
                break;
            case SYSTEM_RUNTIME_INFO_RELAY_ON_TIME:
                updateProperty("relayOnTime", formatDuration(pkt.getInt()));
                break;
            case SYSTEM_RUNTIME_INFO_SYSTEM_RUNTIME:
                updateProperty("systemRunTime", formatDuration(pkt.getInt()));
                break;
        }
    }

    // Convert duration in seconds to (years, days, hours, minutes),
    // just like the original application.
    private String formatDuration(int seconds) {
        long d = Integer.toUnsignedLong(seconds);
        long years = d / 31536000;
        long d2 = d % 31536000;
        long days = d2 / 86400;
        long d3 = d2 % 86400;
        long hours = d3 / 3600;
        long d4 = d3 % 3600;
        long minutes = d4 / 60;

        return String.valueOf(years) + " years" + days + " days " + hours + " hours " + minutes + " minutes";
    }

    @Override
    public void ping() {
        // Ping our device. Just request anything.
        // This method is called from within PeerConnectionHandler when
        // it notices that the communication seems to have stalled.
        connHandler.SendPacket(new DeviSmart.Packet(DOMINION_HEATING, HEATING_TEMPERATURE_FLOOR));
    }

    // Support methods for PeerConnectionHandler
    // Unfortunately Java doesn't support multiple inheritance, so this is
    // a small hack to simulate it

    @Override
    public void reportStatus(@NonNull ThingStatus status, @NonNull ThingStatusDetail statusDetail,
            @Nullable String description) {
        updateStatus(status, statusDetail, description);
    }

    @Override
    public void reportStatus(@NonNull ThingStatus status) {
        updateStatus(status);
    }

    @Override
    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }
}
