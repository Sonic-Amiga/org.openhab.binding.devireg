package org.openhab.binding.devireg.internal;

import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.opensdg.protocol.DeviSmart;

public interface ISDGPeerHandler {

    public void reportStatus(@NonNull ThingStatus status, @NonNull ThingStatusDetail statusDetail, String description);

    public void reportStatus(@NonNull ThingStatus status);

    public void handlePacket(DeviSmart.@NonNull Packet pkt);

    ScheduledExecutorService getScheduler();

}
