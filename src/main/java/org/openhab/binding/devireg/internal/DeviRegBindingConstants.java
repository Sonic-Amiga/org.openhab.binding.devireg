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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link DeviRegBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Pavel Fedin - Initial contribution
 */
@NonNullByDefault
public class DeviRegBindingConstants {

    private static final String BINDING_ID = "devireg";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_DEVIREG_SMART = new ThingTypeUID(BINDING_ID, "devismart");

    // List of all Channel ids
    public static final String CHANNEL_TEMPERATURE_FLOOR = "temperature_floor";
    public static final String CHANNEL_TEMPERATURE_ROOM = "temperature_room";
}
