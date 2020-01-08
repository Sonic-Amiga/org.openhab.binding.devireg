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
package org.openhab.binding.devireg.internal;

import static org.openhab.binding.devireg.internal.DeviRegBindingConstants.*;
import static org.opensdg.protocol.DeviSmart.MsgClass.*;
import static org.opensdg.protocol.DeviSmart.MsgCode.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.measure.quantity.Temperature;
import javax.xml.bind.DatatypeConverter;

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
import org.eclipse.smarthome.core.types.State;
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
public class DeviRegHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(DeviRegHandler.class);

    private @Nullable DeviRegConfiguration config;
    private byte @Nullable [] peerId;
    private @Nullable DeviSmartConnection connection;
    private @Nullable Future<?> reconnectReq;
    private final ExecutorService singleThread = Executors.newSingleThreadExecutor();
    private byte currentMode = -1;
    private Hashtable<String, State> lastState = new Hashtable<String, State>();
    private DeviSmart.@Nullable Version firmwareVer;
    private int firmwareBuild = -1;

    public DeviRegHandler(Thing thing) {
        super(thing);
    }

    @SuppressWarnings("null")
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String ch = channelUID.getId();
        if (command instanceof RefreshType) {
            State value = lastState.get(ch);

            if (value != null) {
                updateState(ch, value);
            }
        } else {
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
            }
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
            return;
        }

        SendPacket(new DeviSmart.Packet(msgClass, msgCode, newTemperature));
    }

    private void setSwitch(int msgClass, int msgCode, Command command) {
        if (command instanceof OnOffType) {
            SendPacket(new DeviSmart.Packet(msgClass, msgCode, command.equals(OnOffType.ON)));
        }
    }

    private void setMode(Command command) {
        if (command instanceof StringType) {
            try {
                String cmdString = command.toString();
                // We are going to send more than one packet, let's collect them and send
                // together.
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                logger.trace("setMode " + command);

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

                if (connection != null) {
                    connection.Send(buffer.toByteArray());
                }

            } catch (IOException e) {
                // We should never get here
                logger.error("Error building control mode packet(s): " + e.toString());
            }
        }
    }

    private void SendPacket(DeviSmart.Packet pkt) {
        if (connection != null) {
            connection.Send(pkt.getBuffer());
        }
    }

    @SuppressWarnings("null")
    @Override
    public void initialize() {
        logger.trace("initialize()");
        DanfossGridConnection.AddUser();

        config = getConfigAs(DeviRegConfiguration.class);

        peerId = SDGUtils.ParseKey(config.peerId);
        if (peerId == null) {
            logger.error("Peer ID is not set");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, "Peer ID is not set");
            return;
        }

        // set the thing status to UNKNOWN temporarily and let the background task decide for the real status.
        // the framework is then able to reuse the resources from the thing handler initialization.
        // we set this upfront to reliably check status updates in unit tests.
        updateStatus(ThingStatus.UNKNOWN);

        connection = new DeviSmartConnection(this);
        connect();
    }

    @Override
    public void dispose() {
        logger.trace("dispose()");

        singleThread.execute(() -> {
            DeviSmartConnection conn = connection;
            connection = null;

            if (reconnectReq != null) {
                reconnectReq.cancel(false);
                reconnectReq = null;
            }

            if (conn != null) {
                conn.SetBlockingMode(true);
                conn.Close();
                logger.info("Connection closed");
                conn.Dispose();
            }
        });

        DanfossGridConnection.RemoveUser();
    }

    public void setOnlineStatus() {
        logger.info("Connection established");
        updateStatus(ThingStatus.ONLINE);
    }

    public void setOfflineStatus(String reason) {
        logger.error(reason);
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, reason);
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        reconnectReq = scheduler.schedule(() -> {
            connect();
        }, 10, TimeUnit.SECONDS);
    }

    @SuppressWarnings("null")
    private void connect() {
        // In order not to mess up our connection state we need to make sure
        // that any two calls are never running concurrently. We use
        // singleThreadExecutorService for this purpose
        singleThread.execute(() -> {
            if (connection == null) {
                return; // Stale Reconnect request from deleted/disabled Thing
            }

            DanfossGridConnection grid = DanfossGridConnection.get();

            if (grid == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                        "Danfoss grid connection failed");
                scheduleReconnect();
            }

            logger.info("Connecting to peer " + DatatypeConverter.printHexBinary(peerId));
            connection.ConnectToRemote(grid, peerId, DeviSmart.ProtocolName);
        });
    }

    private void reportTemperature(String ch, double temp) {
        reportState(ch, new QuantityType<Temperature>(new DecimalType(temp), SIUnits.CELSIUS));
    }

    private void reportSwitch(String ch, boolean on) {
        reportState(ch, OnOffType.from(on));
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

        reportState(CHANNEL_CONTROL_MODE, StringType.valueOf(mode));
        reportState(CHANNEL_CONTROL_STATE, StringType.valueOf(state));
    }

    private void reportState(String ch, State value) {
        // Unfortunately we seem to have no way to request arbitrary value
        // from the thermostat, so we remember all the values in a dictionary
        // in order to be able to handle RefreshType command
        lastState.put(ch, value);
        updateState(ch, value);
    }

    private void reportFirmware() {
        if (firmwareVer != null && firmwareBuild != -1) {
            updateProperty(Thing.PROPERTY_FIRMWARE_VERSION,
                    firmwareVer.toString() + "." + String.valueOf(firmwareBuild));
        }
    }

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
        }
    }
}
