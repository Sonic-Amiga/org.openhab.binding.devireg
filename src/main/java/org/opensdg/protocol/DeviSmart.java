package org.opensdg.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class DeviSmart {
    public static class Packet {
        /*
         * Packet header consists of 4 bytes:
         * - 1 byte : MsgClass
         * - 2 bytes: MsgCode
         * - 1 byte: Payload length, not including the header itself
         */
        public static final int HeaderSize = 4;

        private ByteBuffer m_Buffer;
        private int m_Start;

        // This constructor is used in order to parse incoming packets
        public Packet(byte[] data, int startOffset) {
            m_Buffer = ByteBuffer.wrap(data);
            m_Start = startOffset;
            m_Buffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        // Constructors below are used for outgoing packets
        public Packet(byte msgClass, int msgCode, int payloadLength) {
            // Outgoing packet is always prefixed with one byte.
            // Original app sets it to 1 for packets with no payload (used e.g. for pings);
            // and 0 for all other packets.
            m_Buffer = ByteBuffer.allocate(1 + HeaderSize + payloadLength);
            m_Start = 1;
            m_Buffer.order(ByteOrder.LITTLE_ENDIAN);
            m_Buffer.put((byte) (payloadLength == 0 ? 1 : 0));
            m_Buffer.put(msgClass);
            m_Buffer.putShort((short) msgCode);
            m_Buffer.put((byte) payloadLength);
        }

        public Packet(int msgClass, int msgCode, byte data) {
            this((byte) msgClass, msgCode, 1);
            m_Buffer.put(data);
        }

        public Packet(int msgClass, int msgCode, short data) {
            this((byte) msgClass, msgCode, 2);
            m_Buffer.putShort(data);
        }

        public Packet(int msgClass, int msgCode, int data) {
            this((byte) msgClass, msgCode, 4);
            m_Buffer.putInt(data);
        }

        public Packet(int msgClass, int msgCode, double data) {
            this(msgClass, msgCode, (short) (data * 100));
        }

        public Packet(int msgClass, int msgCode, boolean data) {
            this(msgClass, msgCode, (byte) (data ? 1 : 0));
        }

        public Packet(int msgClass, int msgCode, byte[] data) {
            this((byte) msgClass, msgCode, data.length + 1);
            putByte(data.length);
            m_Buffer.put(data);
        }

        public Packet(int msgClass, int msgCode, String data) {
            this(msgClass, msgCode, data.getBytes());
        }

        public void position(int offset) {
            m_Buffer.position(m_Start + HeaderSize + offset);
        }

        // Argument is int for convenience because all Java expressions return int
        public void putByte(int data) {
            m_Buffer.put((byte) data);
        }

        public void putDate(Date date) {
            Calendar c = Calendar.getInstance();
            c.setTime(date);
            c.setTimeZone(TimeZone.getTimeZone("UTC"));

            int day = c.get(Calendar.DAY_OF_MONTH);
            int dow = c.get(Calendar.DAY_OF_WEEK) - 1;

            // Move SUNDAY to the end
            if (dow == 0) {
                dow = 7;
            }

            putByte(c.get(Calendar.SECOND));
            putByte(c.get(Calendar.MINUTE));
            putByte(c.get(Calendar.HOUR_OF_DAY));
            putByte(day | (dow << 5));
            putByte(c.get(Calendar.MONTH) + 1);
            putByte(c.get(Calendar.YEAR) - 2000);
        }

        public byte[] getBuffer() {
            return m_Buffer.array();
        }

        public int getMsgClass() {
            return Byte.toUnsignedInt(m_Buffer.get(m_Start));
        }

        public int getMsgCode() {
            return Short.toUnsignedInt(m_Buffer.getShort(m_Start + 1));
        }

        public int getLength() {
            return Byte.toUnsignedInt(m_Buffer.get(m_Start + 3)) + HeaderSize;
        }

        // Following methods return payload, interpreted as respective format
        public byte getByte() {
            return m_Buffer.get(m_Start + HeaderSize);
        }

        public short getShort() {
            return m_Buffer.getShort(m_Start + HeaderSize);
        }

        public int getInt() {
            return m_Buffer.getInt(m_Start + HeaderSize);
        }

        public boolean getBoolean() {
            return getByte() != 0;
        }

        public double getDecimal() {
            // Decimal values are 16-bit fixed-point with two decimal places
            return getShort() / 100.0;
        }

        public byte[] getArray() {
            // Arrays and strings are prefixed with length byte
            int length = Byte.toUnsignedInt(getByte());
            byte[] data = new byte[length];

            m_Buffer.get(data, m_Start + HeaderSize + 1, length);
            return data;
        }

        public String getString() {
            return new String(getArray());
        }

        public Date getDate(int offset) {
            position(offset);

            // Date is encoded as 6 bytes: year month day hour minute second in UTC
            // Months count starts from 1; year count begins from 2000
            byte sec = m_Buffer.get();
            byte min = m_Buffer.get();
            int hr = m_Buffer.get() & 63; // No idea why 63 here; DeviSmart app does this
            int d = m_Buffer.get() & 31; // Strip day of week
            int m = m_Buffer.get() - 1;
            int y = Byte.toUnsignedInt(m_Buffer.get()) + 2000; // Greetings year 2256! :)

            Calendar c = Calendar.getInstance();
            c.clear();
            c.setTimeZone(TimeZone.getTimeZone("UTC"));
            c.set(y, m, d, hr, min, sec);
            return c.getTime();
        }
    }

    public static class AwayPacket extends Packet {
        public AwayPacket(Date start, Date end) {
            super((byte) MsgClass.DOMINION_SCHEDULER, MsgCode.SCHEDULER_AWAY, 14);
            setDate(0, start);
            setDate(7, end);
        }

        private void setDate(int offset, Date d) {
            position(offset);
            if (d != null) {
                putByte(1);
                putDate(d);
            } else {
                putByte(0);
            }
        }
    }

    /*
     * Message class. Codes (defined below) appear only under their class,
     * so this is kinda redundant.
     */
    public static class MsgClass {
        public static final int DEVICEGLOBAL = 0;
        public static final int TESTANDPRODUCTION = 1;
        public static final int WIFI = 2;
        public static final int MDG = 3;
        public static final int SOFTWAREUPDATE = 4;
        public static final int DOMINION_SYSTEM = 5;
        public static final int DOMINION_HEATING = 6;
        public static final int DOMINION_SCHEDULER = 7;
        public static final int DOMINION_LOGS = 8;
    };

    /*
     * Message codes. "Len" field specifies actual length of the field, received from
     * the device. For strings and arrays the data has a fixed length with unused portion
     * in the end.
     * This is probably a result of keeping device's firmware as small and simple as possible.
     * It looks like the device has fixed amount of memory space to store its state and
     * configuration items.
     * Consequently, it's not a good idea to send messages longer than specified here. It can
     * cause buffer overflow, damaging the configuration, crashing and potentionally bricking
     * the thermostat.
     * See class Packet above for details on particular data types encoding.
     */
 // @formatter:off
    public static class MsgCode
    {                                                                               /* Len Type      Meaning */
      /* Class DEVICEGLOBAL */
      public static final int GLOBAL_POWERCYCLECOUNTER                     = 2;     /* 1   byte      ??? */
      public static final int GLOBAL_SOFTWAREBUILDREVISION                 = 8;     /* 2   short     Build number. DeviSmart app shows SW version as major.minor.build. */
      public static final int GLOBAL_NUMBEROFENDPOINTS                     = 16;    /* 1   byte      ??? */
      public static final int GLOBAL_DIVISIONID                            = 17;    /* 1   byte      ??? */
      public static final int GLOBAL_BRANDID                               = 18;    /* 1   byte      Brand ID ? Were they going to sell the infrastructure to OEMs ? Used for pings by app */
      public static final int GLOBAL_PRODUCTID                             = 19;    /* 1   short     Some product code? 0x1001 for my DEVIReg Smart */
      public static final int GLOBAL_COUNTRYISOCODE                        = 20;    /* 4   String ?  Reads "00 " (with space) on device sold in Russia. Unused ? */
      public static final int GLOBAL_REVISION                              = 21;    /* 4   int       ??? */
      public static final int GLOBAL_SERIALNUMBER                          = 22;    /* 4   int       Device serial number */
      public static final int GLOBAL_TEXTREVISION                          = 23;    /* 2   short     ??? */
      public static final int GLOBAL_AVAILABLEENDPOINTS                    = 24;    /* 32  ?         Unknown */
      public static final int GLOBAL_HARDWAREREVISION                      = 34;    /* 2   Version   Hardware version */
      public static final int GLOBAL_SOFTWAREREVISION                      = 35;    /* 2   Version   software version */
      public static final int GLOBAL_PRODUCTIONDATE                        = 44;    /* 6   ?         Format yet unknown */
      /* Class TESTANDPRODUCTION */
      public static final int TESTANDPRODUCTION_ERROR_CODE                 = 1008;  /* 2   short     Usage and values are unknown */
      public static final int TESTANDPRODUCTION_RESET_LEVEL                = 4197;  /* 1   byte      ??? */
      public static final int TESTANDPRODUCTION_DEVICE_DESCRIPTION         = 29696; /* 33  String ?  Zeroes on my device. Unused ? */
      public static final int TESTANDPRODUCTION_SUPPLY_POWER               = 29698; /* 1   byte      ??? */
      public static final int TESTANDPRODUCTION_RESTARTSIMPLELINK          = 29699; /* 1   byte      ??? */
      public static final int TESTANDPRODUCTION_IS_RESTARTING_SIMPLELINK   = 29700; /* 1   boolean ? "Operation in progress" indicator ? */
      public static final int TESTANDPRODUCTION_LED                        = 29701; /* 5   ?         LED state ? */
      public static final int TESTANDPRODUCTION_PULLUPS                    = 29702; /* 1   byte      State of internal MCU pullups ? For manufacturing ? */
      public static final int TESTANDPRODUCTION_RELAY                      = 29703; /* 1   byte      Perhaps boolean on/off ? */
      public static final int TESTANDPRODUCTION_BUTTONS                    = 29704; /* 1   byte      Buttons state ? Bit field ? */
      public static final int TESTANDPRODUCTION_UNCOMPENSATED_ROOM         = 29705; /* 2   short     Raw value from temperature sensor ? */
      public static final int TESTANDPRODUCTION_TRANCEIVE                  = 29706; /* 1   byte      ??? */
      /* Class WIFI */
      public static final int WIFI_ERROR_CODE                              = 1009;  /* 2   short     Self-descriptive, but values are unknown */
      public static final int WIFI_ROLE                                    = 29760; /* 1   byte      ??? */
      public static final int WIFI_RESET                                   = 29761; /* 1   byte      ??? */
      public static final int WIFI_OPERATIONAL_STATE                       = 29762; /* 1   byte      ??? */
      public static final int WIFI_CHANNEL                                 = 29763; /* 1   byte      Current channel number ? */
      public static final int WIFI_SSID_AP                                 = 29767; /* 33  String    SSID to use for ad-hoc mode */
      public static final int WIFI_CONNECTED_SSID                          = 29768; /* 33  String    Currently used SSID */
      public static final int WIFI_CONNECT_SSID                            = 29769; /* 33  String    Currently used SSID. Difference from the above is unclear */
      public static final int WIFI_CONNECTED_STRENGTH                      = 29804; /* 2   short     Signal strength; units are unknown */
      public static final int WIFI_CONNECT_KEY                             = 29770; /* 64  String    Wi-fi network key to use */
      public static final int WIFI_CONNECT                                 = 29771; /* 1   byte      ??? */
      public static final int WIFI_NETWORK_PROCESSOR_POWER                 = 29773; /* 1   byte      ??? */
      public static final int WIFI_MAX_LONG_SLEEP                          = 29775; /* 2   short     ??? */
      public static final int WIFI_TX_POWER                                = 29776; /* 1   byte      Power limit ? 0 on my device */
      public static final int WIFI_MDG_READY_FOR_RESTART                   = 29780; /* 1   boolean ? ??? */
      public static final int WIFI_NVM_READY_FOR_RESTART                   = 29781; /* 1   boolean ? NVM = Non Volatile Memory ? */
      public static final int WIFI_SCAN_SSID_0                             = 29782; /* 33  String    Discovered wi-fi networks, up to 10 */
      public static final int WIFI_SCAN_SSID_1                             = 29783;
      public static final int WIFI_SCAN_SSID_2                             = 29784;
      public static final int WIFI_SCAN_SSID_3                             = 29785;
      public static final int WIFI_SCAN_SSID_4                             = 29786;
      public static final int WIFI_SCAN_SSID_5                             = 29787;
      public static final int WIFI_SCAN_SSID_6                             = 29788;
      public static final int WIFI_SCAN_SSID_7                             = 29789;
      public static final int WIFI_SCAN_SSID_8                             = 29790;
      public static final int WIFI_SCAN_SSID_9                             = 29791;
      public static final int WIFI_SCAN_STRENGTH_0                         = 29792; /* 1   byte      Signal strength ? Units are unknown */
      public static final int WIFI_SCAN_STRENGTH_1                         = 29793; /*               Why are these different from WIFI_CONNECTED_STRENGTH ??? */
      public static final int WIFI_SCAN_STRENGTH_2                         = 29794;
      public static final int WIFI_SCAN_STRENGTH_3                         = 29795;
      public static final int WIFI_SCAN_STRENGTH_4                         = 29796;
      public static final int WIFI_SCAN_STRENGTH_5                         = 29797;
      public static final int WIFI_SCAN_STRENGTH_6                         = 29798;
      public static final int WIFI_SCAN_STRENGTH_7                         = 29799;
      public static final int WIFI_SCAN_STRENGTH_8                         = 29800;
      public static final int WIFI_SCAN_STRENGTH_9                         = 29801;
      public static final int WIFI_DISCONNECT_COUNT                        = 29802; /* 2   short     Count of network outages ? For what period ? 0 on my device */
      public static final int WIFI_SKIP_AP_MODE                            = 29803; /* 1   boolean ?   ??? */
      public static final int WIFI_UPDATE_CONNECTED_STRENGTH               = 29805; /* 1   byte      Perhaps used as a command ? */
      /* Class MDG */
      public static final int MDG_ERROR_CODE                               = 1010;  /* 2   short     Error code from MDG. Cloud connection perhaps ? */
      public static final int MDG_CONNECTED_TO_SERVER                      = 29825; /* 1   boolean   MDG cloud connection presence */
      public static final int MDG_SHOULD_CONNECT                           = 29826; /* 1   boolean ? Disables cloud operation ??? */
      public static final int MDG_PAIRING_COUNT                            = 29828; /* 1   int       Number of active pairings */
      public static final int MDG_PAIRING_0_ID                             = 29952; /* 33  byte[]    Peer ID for this pairing */
      public static final int MDG_PAIRING_0_DESCRIPTION                    = 29953; /* 33  String    DEVISmart app stores user name here */
      public static final int MDG_PAIRING_0_PAIRING_TIME                   = 29954; /* 6   ?         When the pairing was created */
      public static final int MDG_PAIRING_0_PAIRING_TYPE                   = 29955; /* 1   byte      Application type. 6 for Android. Likely determines push notifications provider */
      public static final int MDG_PAIRING_0_NOTIFICATION_TOKEN             = 30476; /* 255 String    Token for push notifications, ecosystem-specific */
      public static final int MDG_PAIRING_0_NOTIFICATION_SUBSCRIPTIONS     = 30477; /* 4   int       Enabled notifications, likely bit field. Not usable outside of mobile ecosystem. */
      public static final int MDG_PAIRING_1_ID                             = 29957; /*               These fields repeat for all 10 possible pairings */
      public static final int MDG_PAIRING_1_DESCRIPTION                    = 29958;
      public static final int MDG_PAIRING_1_PAIRING_TIME                   = 29959;
      public static final int MDG_PAIRING_1_PAIRING_TYPE                   = 29960;
      public static final int MDG_PAIRING_1_NOTIFICATION_TOKEN             = 30494;
      public static final int MDG_PAIRING_1_NOTIFICATION_SUBSCRIPTIONS     = 30495;
      public static final int MDG_PAIRING_2_ID                             = 29962;
      public static final int MDG_PAIRING_2_DESCRIPTION                    = 29963;
      public static final int MDG_PAIRING_2_PAIRING_TIME                   = 29964;
      public static final int MDG_PAIRING_2_PAIRING_TYPE                   = 29965;
      public static final int MDG_PAIRING_2_NOTIFICATION_TOKEN             = 30478;
      public static final int MDG_PAIRING_2_NOTIFICATION_SUBSCRIPTIONS     = 30479;
      public static final int MDG_PAIRING_3_ID                             = 29967;
      public static final int MDG_PAIRING_3_DESCRIPTION                    = 29968;
      public static final int MDG_PAIRING_3_PAIRING_TIME                   = 29969;
      public static final int MDG_PAIRING_3_PAIRING_TYPE                   = 29970;
      public static final int MDG_PAIRING_3_NOTIFICATION_TOKEN             = 30480;
      public static final int MDG_PAIRING_3_NOTIFICATION_SUBSCRIPTIONS     = 30481;
      public static final int MDG_PAIRING_4_ID                             = 29972;
      public static final int MDG_PAIRING_4_DESCRIPTION                    = 29973;
      public static final int MDG_PAIRING_4_PAIRING_TIME                   = 29974;
      public static final int MDG_PAIRING_4_PAIRING_TYPE                   = 29975;
      public static final int MDG_PAIRING_4_NOTIFICATION_TOKEN             = 30482;
      public static final int MDG_PAIRING_4_NOTIFICATION_SUBSCRIPTIONS     = 30483;
      public static final int MDG_PAIRING_5_ID                             = 29977;
      public static final int MDG_PAIRING_5_DESCRIPTION                    = 29978;
      public static final int MDG_PAIRING_5_PAIRING_TIME                   = 29979;
      public static final int MDG_PAIRING_5_PAIRING_TYPE                   = 29980;
      public static final int MDG_PAIRING_5_NOTIFICATION_TOKEN             = 30484;
      public static final int MDG_PAIRING_5_NOTIFICATION_SUBSCRIPTIONS     = 30485;
      public static final int MDG_PAIRING_6_ID                             = 29982;
      public static final int MDG_PAIRING_6_DESCRIPTION                    = 29983;
      public static final int MDG_PAIRING_6_PAIRING_TIME                   = 29984;
      public static final int MDG_PAIRING_6_PAIRING_TYPE                   = 29985;
      public static final int MDG_PAIRING_6_NOTIFICATION_TOKEN             = 30486;
      public static final int MDG_PAIRING_6_NOTIFICATION_SUBSCRIPTIONS     = 30487;
      public static final int MDG_PAIRING_7_ID                             = 29987;
      public static final int MDG_PAIRING_7_DESCRIPTION                    = 29988;
      public static final int MDG_PAIRING_7_PAIRING_TIME                   = 29989;
      public static final int MDG_PAIRING_7_PAIRING_TYPE                   = 29990;
      public static final int MDG_PAIRING_7_NOTIFICATION_TOKEN             = 30488;
      public static final int MDG_PAIRING_7_NOTIFICATION_SUBSCRIPTIONS     = 30489;
      public static final int MDG_PAIRING_8_ID                             = 29992;
      public static final int MDG_PAIRING_8_DESCRIPTION                    = 29993;
      public static final int MDG_PAIRING_8_PAIRING_TIME                   = 29994;
      public static final int MDG_PAIRING_8_PAIRING_TYPE                   = 29995;
      public static final int MDG_PAIRING_8_NOTIFICATION_TOKEN             = 30490;
      public static final int MDG_PAIRING_8_NOTIFICATION_SUBSCRIPTIONS     = 30491;
      public static final int MDG_PAIRING_9_ID                             = 29997;
      public static final int MDG_PAIRING_9_DESCRIPTION                    = 29998;
      public static final int MDG_PAIRING_9_PAIRING_TIME                   = 29999;
      public static final int MDG_PAIRING_9_PAIRING_TYPE                   = 30000;
      public static final int MDG_PAIRING_9_NOTIFICATION_TOKEN             = 30492;
      public static final int MDG_PAIRING_9_NOTIFICATION_SUBSCRIPTIONS     = 30493;
      public static final int MDG_PRIVATE_KEY                              = 30464; // 33  byte[] ?  Likely stores device's private key. Not sent upon connecting.
      public static final int MDG_REVOKE_SPECIFIC_PAIRING                  = 30466; // 33  byte[] ?  Probably used as revocation command. Reads all zeroes on my device
      public static final int MDG_REVOKE_ALL_PAIRINGS                      = 30467; // 1   byte      Probably to be used as a command. Reads zero.
      public static final int MDG_LICENCE_KEY                              = 30468; // ?   byte[] ?  Likely stores device's license key. Not sent upon connecting.
      public static final int MDG_RANDOM_BYTES                             = 30469; // ?   byte[] ?  May be RNG seed
      public static final int MDG_CONNECTION_COUNT                         = 30470; // 1   byte      Number of active connections ?
      public static final int MDG_PENDING_PAIRING                          = 30471; // 33  byte[] ?  Looks like peer ID; purpose is unknown. Reads zeroes on my device
      public static final int MDG_ADD_PAIRING                              = 30472; // 33  byte[]    Original app always sets this to own peerID upon connection. Purpose is not known.
      public static final int MDG_SERVER_DISCONNECT_COUNT                  = 30473; // 2   short ?   The name is self-descriptive, reads 0 on my device
      public static final int MDG_PAIRING_NOTIFICATION_TOKEN               = 30474; // 255
      public static final int MDG_PAIRING_NOTIFICATION_SUBSCRIPTIONS       = 30475; // 4
      public static final int MDG_PAIRING_DESCRIPTION                      = 30496; // 33
      public static final int MDG_PAIRING_TYPE                             = 30497; // 1
      public static final int MDG_CONFIRM_SYSTEM_WIZARD_INFO               = 30498; // 7
      /* Class SOFTWAREUPDATE */
      public static final int SOFTWAREUPDATE_ERROR_CODE                    = 1011;
      public static final int SOFTWAREUPDATE_DOWNLOAD_PUSHED_UPDATE        = 30593;
      public static final int SOFTWAREUPDATE_CHECK_FOR_UPDATE              = 30594;
      public static final int SOFTWAREUPDATE_INSTALLATION_STATE            = 30595;
      public static final int SOFTWAREUPDATE_INSTALLATION_PROGRESS         = 30596;
      /* Class DOMINION_SYSTEM */
      public static final int SYSTEM_RUNTIME_INFO_RELAY_COUNT              = 29232;
      public static final int SYSTEM_RUNTIME_INFO_RELAY_ON_TIME            = 29233;
      public static final int SYSTEM_RUNTIME_INFO_SYSTEM_RUNTIME           = 29234;
      public static final int SYSTEM_RUNTIME_INFO_SYSTEM_RESETS            = 29235;
      public static final int SYSTEM_TIME_ISVALID                          = 29236; // 1   boolean   Self-descriptive. Reads 1 = true on my device.
      public static final int SYSTEM_TIME                                  = 29237;
      public static final int SYSTEM_TIME_OFFSET                           = 29238; // 2   short     GMT offset in minutes
      public static final int SYSTEM_WIZARD_INFO                           = 29239;
      public static final int SYSTEM_HEATING_INFO                          = 29240;
      public static final int SYSTEM_ALARM_INFO = 29241;
      public static final int SYSTEM_WINDOW_OPEN = 29242;
      public static final int SYSTEM_INFO_FLOOR_SENSOR_CONNECTED = 29243;
      public static final int SYSTEM_INFO_FORECAST_ENABLED = 29244;
      public static final int SYSTEM_INFO_BREAKOUT = 29245;
      public static final int SYSTEM_INFO_WINDOW_OPEN_DETECTION = 29246;
      public static final int SYSTEM_UI_BRIGTHNESS = 29247;
      public static final int SYSTEM_UI_SCREEN_OFF = 29248;
      public static final int SYSTEM_LOCAL_CONFIRM_REQUEST = 29249;
      public static final int SYSTEM_ROOM_NAME                             = 29250; // 33  String    Room name for this thermostat
      public static final int SYSTEM_HOUSE_NAME                            = 29251; // 33  String    House name for this thermostat
      public static final int SYSTEM_ZONE_NAME                             = 29252; // 33  String    Zone name for this thermostat
      public static final int SYSTEM_READY_RESTART                         = 29253;
      public static final int SYSTEM_LOCAL_CONFIRM_RESPONSE                = 29254;
      public static final int SYSTEM_TIME_OFFSET_TABLE                     = 29255;
      public static final int NVM_CONF_SYSTEM_WIZARD                       = 29256;
      public static final int NVM_HEATCONTROLLER_INTEGRATORS               = 29257;
      public static final int NVM_AWAY_PLAN                                = 29258;
      public static final int NVM_WEEK_PLAN                                = 29259;
      public static final int NVM_SCHEDULER_MODE                           = 29260;
      public static final int NVM_SCHEDULER_TIME                           = 29261;
      public static final int NVM_SETPOINTS_CONF                           = 29262; // 25  byte[]    Looks like raw NVRAM dump for all scheduler setpoints
      public static final int NVM_TRACING = 29263;
      public static final int NVM_RUNTIME_STATS = 29264;
      public static final int NVM_HOME_EARLY = 29265;
      public static final int NVM_CREDENTIALS = 29266;
      public static final int NVM_DEFAULT_HEATCONTROLLER_INTEGRATORS       = 29267;
      public static final int SYSTEM_UI_SAFETY_LOCK                        = 29268;
      public static final int NVM_CONSUMPTION_HISTORY                      = 29269;
      public static final int NVM_POWER_CONSUMPTION_HISTORY_LAST_SAVED_DAY = 29270;
      public static final int SYSTEM_MDG_CONNECT_PROGRESS                  = 29271;
      public static final int SYSTEM_MDG_CONNECT_PROGRESS_MAX              = 29272;
      public static final int SYSTEM_MDG_CONNECT_ERROR                     = 29273;
      public static final int SYSTEM_MDG_LOG_UNTIL                         = 29274;
      public static final int NVM_SYSTEM_PEAK_GRADIENT                     = 29275;
      /* Class DOMINION_HEATING */
      public static final int HEATING_TEMPERATURE_TOP                      = 29296; // 2   decimal   Unknown
      public static final int HEATING_TEMPERATURE_BOTTOM                   = 29297; // 2   decimal   Unknown
      public static final int HEATING_TEMPERATURE_FLOOR                    = 29298; // 2   decimal   Current floor temperature reading
      public static final int HEATING_TEMPERATURE_ROOM                     = 29299; // 2   decimal   Current room temperature reading
      public static final int HEATING_LOW_TEMPERATURE_WARNING              = 29300; // 2   decimal   Setting for "low temperature" push notification
      public static final int HEATING_LOW_TEMPERATURE_WARNING_THRESHOLD    = 29301; // 2   decimal   Unclear. Set to 1 on my device.
      /* Class DOMINION_SCHEDULER */
      public static final int SCHEDULER_CONTROL_INFO                       = 29328; // 1   byte      Current control mode. See ControlState values below.
      public static final int SCHEDULER_CONTROL_MODE                       = 29329; // 1   byte      Mode change request; shows last command on read. See ControlMode values below
      public static final int SCHEDULER_SETPOINT_COMFORT                   = 29330; // 2   decimal   Temperature setting for "At home" period
      public static final int SCHEDULER_SETPOINT_ECONOMY                   = 29331; // 2   decimal   Temperature setting for "Away/asleep" period
      public static final int SCHEDULER_SETPOINT_MANUAL                    = 29332; // 2   decimal   Manual mode temperature setting
      public static final int SCHEDULER_SETPOINT_AWAY                      = 29333; // 2   decimal   Vacation mode temperature setting
      public static final int SCHEDULER_SETPOINT_FROST_PROTECTION          = 29334; // 2   decimal   Frost protection temperature setting
      public static final int SCHEDULER_SETPOINT_FLOOR_COMFORT             = 29335; // 2   decimal   Minimal floor temperature setting
      public static final int SCHEDULER_SETPOINT_FLOOR_COMFORT_ENABLED     = 29336; // 1   boolean   Enable keeping minimal floor temperature
      public static final int SCHEDULER_SETPOINT_MAX_FLOOR                 = 29337; // 2   decimal   Maximum floor temperature setting
      public static final int SCHEDULER_SETPOINT_TEMPORARY                 = 29338; // 2   decimal   Not sure what this is
      public static final int SCHEDULER_AWAY_ISPLANNED                     = 29339; // 1   boolean ? Unknown
      public static final int SCHEDULER_AWAY                               = 29340; // 15
      public static final int SCHEDULER_WEEK                               = 29341; // 29
      public static final int SCHEDULER_WEEK_2                             = 29342; // 43
      /* Class DOMINION_LOGS */
      public static final int LOG_RESET                                    = 29376;
      public static final int LOG_ENERGY_CONSUMPTION_TOTAL                 = 29377;
      public static final int LOG_ENERGY_CONSUMPTION_30DAYS                = 29378;
      public static final int LOG_ENERGY_CONSUMPTION_7DAYS                 = 29379;
      public static final int LOG_LATEST_ACTIVITIES                        = 29380;
    };
 // @formatter:on

    public static class ControlState {
        public static final byte Configuring = 0;
        public static final byte Manual = 1;
        public static final byte AtHome = 2;
        public static final byte Away = 3;
        public static final byte Vacation = 4;
        public static final byte Fatal = 5;
        public static final byte Pause = 6;
        public static final byte Off = 7;
        public static final byte AtHomeOverride = 8;
    };

    public static class ControlMode {
        public static final byte WEEKLY_SCHEDULE_ON = 0;
        public static final byte WEEKLY_SCHEDULE_OFF = 1;
        public static final byte OFF_STATE_ON = 2;
        public static final byte OFF_STATE_OFF = 3;
        public static final byte FROST_PROTECTION_ON = 4;
        public static final byte FROST_PROTECTION_OFF = 5;
        public static final byte TEMPORARY_HOME_ON = 6;
        public static final byte TEMPORARY_HOME_OFF = 7;
    };
}
