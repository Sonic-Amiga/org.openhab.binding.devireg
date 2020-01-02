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

import javax.measure.quantity.Temperature;

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
import org.opensdg.protocol.DeviSmart;
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
    private @Nullable DeviSmartConnection connection;

    public DeviRegHandler(Thing thing) {
        super(thing);
        DanfossGridConnection.AddUser();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        switch (channelUID.getId()) {
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

    private void SendPacket(DeviSmart.Packet pkt) {
        if (connection != null) {
            connection.Send(pkt.getBuffer());
        }
    }

    @Override
    public void initialize() {
        logger.trace("initialize()");
        config = getConfigAs(DeviRegConfiguration.class);

        // set the thing status to UNKNOWN temporarily and let the background task decide for the real status.
        // the framework is then able to reuse the resources from the thing handler initialization.
        // we set this upfront to reliably check status updates in unit tests.
        updateStatus(ThingStatus.UNKNOWN);

        // Example for background initialization:
        scheduler.execute(() -> {
            DanfossGridConnection grid = DanfossGridConnection.get();

            if (grid == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                        "Danfoss grid connection failed");
                // TODO: Retry after some time ?
                return;
            }

            byte[] peerId = SDGUtils.ParseKey(config.peerId);
            if (peerId == null) {
                logger.error("Peer ID is not set");
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, "Peer ID is not set");
                return;
            }

            logger.info("Connecting to peer " + config.peerId);
            connection = new DeviSmartConnection(this);
            connection.ConnectToRemote(grid, peerId, "dominion-1.0");
        });
    }

    @Override
    public void dispose() {
        logger.trace("dispose()");
        if (connection != null) {
            connection.SetBlockingMode(true);
            connection.Close();
            connection.Dispose();
            connection = null;
        }
        DanfossGridConnection.RemoveUser();
    }

    public void setOnlineStatus(boolean isOnline) {
        updateStatus(isOnline ? ThingStatus.ONLINE : ThingStatus.OFFLINE);
    }

    private void reportTemperature(String ch, double temp) {
        updateState(ch, new QuantityType<Temperature>(new DecimalType(temp), SIUnits.CELSIUS));
    }

    private void reportSwitch(String ch, boolean on) {
        updateState(ch, OnOffType.from(on));
    }

    private void reportControlInfo(byte info) {
        String mode;

        if (info >= ControlState.Configuring && info <= ControlState.AtHomeOverride) {
            mode = CONTROL_MODES[info];
        } else {
            mode = "";
        }

        updateState(CHANNEL_CONTROL_MODE, StringType.valueOf(mode));
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
        }
    }
}
