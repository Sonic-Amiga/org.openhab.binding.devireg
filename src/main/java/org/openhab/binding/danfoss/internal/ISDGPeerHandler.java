package org.openhab.binding.danfoss.internal;

import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.opensdg.protocol.Dominion;

public interface ISDGPeerHandler {

    public void reportStatus(@NonNull ThingStatus status, @NonNull ThingStatusDetail statusDetail, String description);

    public void reportStatus(@NonNull ThingStatus status);

    public void handlePacket(Dominion.@NonNull Packet pkt);

    public void ping();

    ScheduledExecutorService getScheduler();

}
