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
import static org.opensdg.protocol.DeviSmart.MsgCode.*;

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.opensdg.protocol.DeviSmart;
import org.osgi.service.cm.ConfigurationAdmin;
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

    private ConfigurationAdmin confAdmin;
    private @Nullable DeviRegConfiguration config;
    private @Nullable DeviSmartConnection connection;

    public DeviRegHandler(Thing thing, ConfigurationAdmin configurationAdmin) {
        super(thing);
        confAdmin = configurationAdmin;
        DanfossGridConnection.AddUser();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (CHANNEL_TEMPERATURE_FLOOR.equals(channelUID.getId())) {
            if (command instanceof RefreshType) {
                // TODO: handle data refresh
            }

            // TODO: handle command

            // Note: if communication with thing fails for some reason,
            // indicate that by setting the status with detail information:
            // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
            // "Could not control device at IP address x.x.x.x");
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
            DanfossGridConnection grid = DanfossGridConnection.get(confAdmin);

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
        }
    }
}
