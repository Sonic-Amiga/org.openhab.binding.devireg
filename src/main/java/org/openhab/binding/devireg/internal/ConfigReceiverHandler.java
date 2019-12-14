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

import static org.openhab.binding.devireg.internal.DeviRegBindingConstants.CHANNEL_OTP;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DeviRegHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Pavel Fedin - Initial contribution
 */
@NonNullByDefault
public class ConfigReceiverHandler extends BaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(ConfigReceiverHandler.class);

    public ConfigReceiverHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String channel = channelUID.getId();

        switch (channel) {
            case CHANNEL_OTP:
                if (command instanceof StringType) {
                    logger.debug("Downloading configuration with OTP " + command);
                }
                break;
            default:
                logger.error("Command on invalid channel " + channel);
        }
    }

    @Override
    public void initialize() {
        logger.trace("initialize()");
        DanfossGridConnection.AddUser();

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

            updateStatus(ThingStatus.ONLINE);
        });
    }

    @Override
    public void dispose() {
        logger.trace("dispose()");
        DanfossGridConnection.RemoveUser();
    }
}
