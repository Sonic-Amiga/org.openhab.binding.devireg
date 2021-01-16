package org.openhab.binding.danfoss.discovery;

import static org.opensdg.protocol.Icon.MsgClass.ROOM_FIRST;
import static org.opensdg.protocol.Icon.MsgCode.ROOMNAME;

import java.util.Collections;
import java.util.HashMap;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.danfoss.internal.DanfossBindingConstants;
import org.openhab.binding.danfoss.internal.IconMasterHandler;
import org.openhab.binding.danfoss.internal.IconRoomConfiguration;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingUID;
import org.opensdg.protocol.Dominion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IconRoomDiscoveryService extends AbstractDiscoveryService {

    private final Logger logger = LoggerFactory.getLogger(IconRoomDiscoveryService.class);
    private IconMasterHandler master;

    public IconRoomDiscoveryService(IconMasterHandler bridgeHandler) {
        super(Collections.singleton(DanfossBindingConstants.THING_TYPE_ICON_ROOM), 600, true);
        master = bridgeHandler;
    }

    @Override
    protected void startScan() {
        IconMasterHandler bridge = master;

        if (bridge != null) {
            bridge.scanRooms();
        }
    }

    public void activate() {
        activate(new HashMap<>());
    }

    @Override
    public void deactivate() {
        super.deactivate();
        master = null;
    }

    @Override
    protected void startBackgroundDiscovery() {
        // We have nothing to do here
    }

    @Override
    protected void stopBackgroundDiscovery() {
        // We have nothing to do here
    }

    public void handlePacket(Dominion.@NonNull Packet pkt) {
        IconMasterHandler bridge = master;

        if (bridge == null) {
            return;
        }

        if (pkt.getMsgCode() == ROOMNAME) {
            String name = pkt.getString();

            // Unused rooms are reported as having empty names, ignore them.
            if (!name.isEmpty()) {
                ThingUID brigdeThingUID = bridge.getThing().getUID();
                int number = pkt.getMsgClass() - ROOM_FIRST;

                logger.info("Detected Icon Room #{} \"{}\" on master {}", number, name, brigdeThingUID);

                ThingUID thingUID = new ThingUID(DanfossBindingConstants.THING_TYPE_ICON_ROOM, brigdeThingUID,
                        String.valueOf(number));
                DiscoveryResult result = DiscoveryResultBuilder.create(thingUID)
                        .withProperty(IconRoomConfiguration.ROOM_NUMBER, number)
                        .withLabel("Danfoss Icon room (" + name + ")").withBridge(brigdeThingUID).build();

                thingDiscovered(result);
            }
        }
    }
}
