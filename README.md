# DeviReg Binding

This binding allows you to control DeviReg Smart(tm) smart floor thermostat (https://www.devismart.com/), produced by Danfoss company.
This thermostat communicates over Wi-Fi using a proprietary cloud via a protocol called SecureDeviceGrid(tm) (http://securedevicegrid.com/).
The cloud solution is developed by Trifork company. This binding relies on OpenSDG (https://github.com/Sonic-Amiga/opensdg), the free and opensource implementation of this protocol. The library must be installed in your system in order for this binding to operate.

## Supported Things

At least currently only DeviReg Smart(tm) is supported.

## Discovery

_Describe the available auto-discovery features here. Mention for what it works and what needs to be kept in mind when using it._

## Binding Configuration

_If your binding requires or supports general configuration settings, please create a folder ```cfg``` and place the configuration file ```<bindingId>.cfg``` inside it. In this section, you should link to this file and provide some information about the options. The file could e.g. look like:_

```
# Configuration for the Philips Hue Binding
#
# Default secret key for the pairing of the Philips Hue Bridge.
# It has to be between 10-40 (alphanumeric) characters
# This may be changed by the user for security reasons.
secret=openHABSecret
```

_Note that it is planned to generate some part of this based on the information that is available within ```src/main/resources/ESH-INF/binding``` of your binding._

_If your binding does not offer any generic configurations, you can remove this section completely._

## Thing Configuration

_Describe what is needed to manually configure a thing, either through the (Paper) UI or via a thing-file. This should be mainly about its mandatory and optional configuration parameters. A short example entry for a thing file can help!_

_Note that it is planned to generate some part of this based on the XML files within ```src/main/resources/ESH-INF/thing``` of your binding._

## Channels

| channel                   | type        | description                                    | Read-only |
|---------------------------|-------------|------------------------------------------------|-----------|
| temperature_floor         | Temperature | Floor temperature sensor reading                      | Y |
| temperature_room          | Temperature | Room temperature sensor reading                       | Y |
| setpoint_comfort          | Temperature | Set point for "At home" period                        | N |
| setpoint_economy          | Temperature | Set point for "Away/Asleep" period                    | N |
| setpoint_manual           | Temperature | Set point for manual mode                             | N |
| setpoint_away             | Temperature | Set point for vacation mode                           | N |
| setpoint_antifreeze       | Temperature | Set point for frost protection (paused) mode          | N |
| setpoint_min_floor        | Temperature | Minimum floor temperature                             | N |
| setpoint_min_floor_enable | Switch      | Enable keeping minimum floor temperature              | N |
| setpoint_max_floor        | Temperature | Maximum allowed floor temperature                     | N |
| setpoint_warning          | Temperature | Temperature below which an alarm notification is sent to your phone  | N |
| control_mode              | String      | Chosen control mode: MANUAL, OVERRIDE, SCHEDULE, VACATION, PAUSE, OFF| N |
| control_state             | String      | Current control state: CONFIGURING, MANUAL, HOME, AWAY, VACATION, FATAL, PAUSE, OFF, OVERRIDE | Y |

NOTES:

1. Push notifications are sent directly by the thermostat via original Danfoss cloud infrastructure,
this is part of the original service. OpenHAB takes no part in this process.
2. If there's no "at home" time configured in the schedule for the given day, OVERRIDE mode does not work and immediately falls back to SCHEDULE. This is a bug in thermostat's firmware, OpenHAB does not affect this.

## Full Example

_Provide a full usage example based on textual configuration files (*.things, *.items, *.sitemap)._

## Any custom content here!

_Feel free to add additional sections for whatever you think should also be mentioned about your binding!_
