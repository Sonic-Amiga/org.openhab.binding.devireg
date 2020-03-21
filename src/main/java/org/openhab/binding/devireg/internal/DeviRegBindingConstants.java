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
    public static final ThingTypeUID THING_TYPE_ICON_WIFI = new ThingTypeUID(BINDING_ID, "icon_wifi");
    public static final ThingTypeUID THING_TYPE_ICON_ROOM = new ThingTypeUID(BINDING_ID, "icon_room");

    public static final int ICON_MAX_ROOMS = 45;

    // List of all Channel ids
    public static final String CHANNEL_TEMPERATURE_FLOOR = "temperature_floor";
    public static final String CHANNEL_TEMPERATURE_ROOM = "temperature_room";
    public static final String CHANNEL_SETPOINT_COMFORT = "setpoint_comfort";
    public static final String CHANNEL_SETPOINT_ECONOMY = "setpoint_economy";
    public static final String CHANNEL_SETPOINT_ASLEEP = "setpoint_asleep";
    public static final String CHANNEL_SETPOINT_MANUAL = "setpoint_manual";
    public static final String CHANNEL_SETPOINT_AWAY = "setpoint_away";
    public static final String CHANNEL_SETPOINT_ANTIFREEZE = "setpoint_antifreeze";
    public static final String CHANNEL_SETPOINT_MIN_FLOOR = "setpoint_min_floor";
    public static final String CHANNEL_SETPOINT_MIN_FLOOR_ENABLE = "setpoint_min_floor_enable";
    public static final String CHANNEL_SETPOINT_MAX_FLOOR = "setpoint_max_floor";
    public static final String CHANNEL_SETPOINT_WARNING = "setpoint_warning";
    public static final String CHANNEL_CONTROL_STATE = "control_state";
    public static final String CHANNEL_CONTROL_MODE = "control_mode";
    public static final String CHANNEL_WINDOW_DETECTION = "window_detection";
    public static final String CHANNEL_WINDOW_OPEN = "window_open";
    public static final String CHANNEL_FORECAST = "forecast";
    public static final String CHANNEL_SCREEN_LOCK = "screen_lock";
    public static final String CHANNEL_BRIGHTNESS = "brightness";
    public static final String CHANNEL_BATTERY = "battery";

    public static final String CONTROL_MODE_MANUAL = "MANUAL";
    public static final String CONTROL_MODE_OVERRIDE = "OVERRIDE";
    public static final String CONTROL_MODE_SCHEDULE = "SCHEDULE";
    public static final String CONTROL_MODE_VACATION = "VACATION";
    public static final String CONTROL_MODE_PAUSE = "PAUSE";
    public static final String CONTROL_MODE_OFF = "OFF";
}
