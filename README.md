# Danfoss Binding

This binding allows you to integrate heating solutions by Danfoss company. These solutions communicate over Wi-Fi using a
proprietary cloud via a protocol called SecureDeviceGrid(tm)
(http://securedevicegrid.com/).

The cloud solution is developed by Trifork company. This binding relies on OpenSDG (https://github.com/Sonic-Amiga/opensdg), the
free and opensource implementation of this protocol. The library must be installed in your system in order for this binding to
operate.

## Supported Things

- DeviReg(tm) Smart floor thermostat (https://www.devismart.com/)
- Danfoss Icon controller (https://icon.danfoss.com/)
- Danfors Icon room

Note that Danfoss Icon support is currently incomplete and in beta state. It was done entirely with user's
support; i, the developer, don't have Icon hardware on my disposal.

## Installation and supported architectures

This binding relies on OpenSDG library (https://github.com/Sonic-Amiga/opensdg/releases) for communicating with the hardware.
On Linux OS It is necessary to manually install the library in your system before using this binding. On Windows no extra
components need to be installed.

Currently the following architectures / operating systems are supported:

- Windows / x86_64
- Linux / x86_64
- Linux / i386
- Linux / ARM hardfloat (armhf) 32-bit
- Linux / arm64

## Discovery

Unfortunately automatic discovery of Danfoss devices is technically very problematic and cannot be reliably performed in
OpenHAB (or any other home automation) environment. Instead, it is supposed to set the hardware up using original smartphone
application according to product manual; and then share the configuration with OpenHAB.   

1. Navigate to http://YOUR_OPENHAB_HOST:8080/danfoss/
2. According to instructions in the form, use "Share house" function in your DeviReg(tm) Smart smartphone application.
3. Enter the One Time Password, issued by the phone, in the form and click "Receive"
4. On success a message is displayed, telling number of things received.
5. Navigate to Inbox, review and approve your Things

## Binding Configuration

| Parameter  | Meaning                                                                                  |
|------------|------------------------------------------------------------------------------------------|
| privateKey | Private key, used for communication.                                                     |
| publicKey  | Public key, also known as Peer ID. All devices on the Grid are identified by these keys. |
| userName   | User name, which will represent the OpenHAB in DeviReg(tm) Smart smartphone application. Used for configuration sharing. |

User name is the only parameter to actually be set by the user.

Keys are vital for functioning of the binding. A valid key pair is generated automatically upon first run and stored in the
configuration. Unless you exactly know what you're doing and why, do not modify the private key, or you'll lose access to all
your thermostats and will have to perform configuration sharing again!!! For the sake of safety, keys are made read-only in the
interface, but you still can freely edit them in expert mode. A public key is always derived from the private key and any attempts
to modify it will be reverted. It is provided for information purposes only, should there arise such a need.

## Thing Configuration

### DeviReg Smart and Icon Controller

The only parameter here is peerId. Being thermostat's public key, it identifies the thermostat on the Grid. It is made read-only in
the interface in order to prevent accidental damage. Unfortunately it is not written on the device, nor in the manual. The only way
to obtain this ID is to receive the configuration from the original smartphone application.

### Icon room

roomNumber - ordinal number of the room in Icon system

## Channels

Channels, supported by DeviReg Smart(tm) thermostat:

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
| window_detection          | Switch      | Enable or disable open window detection               | N |
| window_open               | Switch      | Whether an open window is detected in the room        | Y |
| forecast                  | Switch      | Attempt to forecast heating and provide desired temperature at desired time | N |
| screen_lock               | Switch      | Thermostat sensor controls lock                       | N |
| brightness                | Number      | Thermostat screen brightness, from 10 to 100          | Y |
| heating_state             | Switch      | Current heater state, on / off                        | Y |
| on_time_7_days            | Time        | Summary heater on time for the last 7 days            | Y |
| on_time_30_days           | Time        | Summary heater on time for the last 14 days           | Y |
| on_time_total             | Time        | Total heater on time                                  | Y |
| sensor_disconnected       | Switch      | Temperature sensor fault flag: disconnected           | Y |
| sensor_shorted            | Switch      | Temperature sensor fault flag: short circuit          | Y |
| overheat                  | Switch      | Thermostat fault flag: floor overheated               | Y |
| unrecoverable             | Switch      | Internal thermostat fault flag                        | Y |

Channels, supported by Icon Master:

| channel                   | type        | description                                    | Read-only |
|---------------------------|-------------|------------------------------------------------|-----------|
| setpoint_away             | Temperature | Set point for vacation mode                    | N |
| setpoint_antifreeze       | Temperature | Set point for frost protection (paused) mode   | N |

Channels, supported by Icon Room:

| channel                   | type        | description                                    | Read-only |
|---------------------------|-------------|------------------------------------------------|-----------|
| temperature_floor         | Temperature | Floor temperature sensor reading                      | Y |
| temperature_room          | Temperature | Room temperature sensor reading                       | Y |
| setpoint_comfort          | Temperature | Set point for "At home" period                        | N |
| setpoint_economy          | Temperature | Set point for "Away" period                           | N |
| setpoint_asleep           | Temperature | Set point for "Asleep" period                         | N |
| setpoint_min_floor        | Temperature | Minimum floor temperature                             | N |
| setpoint_max_floor        | Temperature | Maximum allowed floor temperature                     | N |
| manual_mode               | Switch      | Manual mode on/off                                    | N |
| control_state             | String      | Current control state: HOME, AWAY, ASLEEP, FATAL      | Y |
| battery                   | Number      | Battery charge percentage for wireless thermostats    | Y |

NOTES:

1. Push notifications are sent directly by the thermostat via original Danfoss cloud infrastructure,
this is part of the original service. OpenHAB takes no part in this process.
2. Communication with the hardware is currently possible only via the cloud infrastructure. Therefore a working Internet connection
is required for the binding to operate, even if the OpenHAB installation resides on the same network with thermostats. This is
a restriction of thermostat's firmware and cannot be fixed.
3. For DeviReg Smart: If there's no "at home" time configured in the schedule for the given day, OVERRIDE mode does not work and
immediately falls back to SCHEDULE. This is a bug in thermostat's firmware, OpenHAB does not affect this.
