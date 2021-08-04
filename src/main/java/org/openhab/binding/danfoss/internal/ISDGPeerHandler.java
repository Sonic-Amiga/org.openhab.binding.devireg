package org.openhab.binding.danfoss.internal;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.openhab.binding.danfoss.internal.protocol.Dominion;

public interface ISDGPeerHandler {

    public void reportStatus(@NonNull ThingStatus status, @NonNull ThingStatusDetail statusDetail, String description);

    public void handlePacket(Dominion.@NonNull Packet pkt);

    public void ping();
}
