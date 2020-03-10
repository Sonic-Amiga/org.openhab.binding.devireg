package org.openhab.binding.devireg.internal;

import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.opensdg.protocol.DeviSmart.MsgCode;
import org.opensdg.protocol.DeviSmart.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IconMasterHandler extends BaseBridgeHandler implements ISDGPeerHandler {

    private final Logger logger = LoggerFactory.getLogger(DeviRegHandler.class);
    private PeerConnectionHandler connHandler = new PeerConnectionHandler(this);

    public IconMasterHandler(Bridge bridge) {
        super(bridge);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // TODO Auto-generated method stub

    }

    @Override
    public void initialize() {
        DeviRegConfiguration config = getConfigAs(DeviRegConfiguration.class);

        connHandler.initialize(config.peerId);
    }

    @Override
    public void dispose() {
        connHandler.dispose();
    }

    @Override
    public void reportStatus(@NonNull ThingStatus status, @NonNull ThingStatusDetail statusDetail, String description) {
        updateStatus(status, statusDetail, description);
    }

    @Override
    public void reportStatus(@NonNull ThingStatus status) {
        updateStatus(status);
    }

    @Override
    public void handlePacket(@NonNull Packet pkt) {
        // TODO Auto-generated method stub
        int msgCode = pkt.getMsgCode();

        // We are going to ask people to give us these dumps; avoid leaking security-sensitive data
        if (msgCode != MsgCode.MDG_PAIRING_0_ID && msgCode != MsgCode.MDG_PAIRING_1_ID
                && msgCode != MsgCode.MDG_PAIRING_2_ID && msgCode != MsgCode.MDG_PAIRING_3_ID
                && msgCode != MsgCode.MDG_PAIRING_4_ID && msgCode != MsgCode.MDG_PAIRING_5_ID
                && msgCode != MsgCode.MDG_PAIRING_6_ID && msgCode != MsgCode.MDG_PAIRING_7_ID
                && msgCode != MsgCode.MDG_PAIRING_8_ID && msgCode != MsgCode.MDG_PAIRING_9_ID) {
            logger.info("Data: " + pkt.toString());
        }
    }

    @Override
    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    @Override
    public void ping() {
        // TODO Auto-generated method stub
    }
}
