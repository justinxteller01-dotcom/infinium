package dev.justin.infinium;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** M6 S2C: another player started/stopped holding flight thrust. Relayed by the server to everyone tracking them, so remote
 *  thrust particles and flight sounds follow synced state (not the remote player's sneak pose). */
public record FlightThrustStatePayload(int entityId, boolean active) implements CustomPacketPayload {
    public static final Type<FlightThrustStatePayload> TYPE = new Type<>(Infinium.id("flight_thrust_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlightThrustStatePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, FlightThrustStatePayload::entityId, ByteBufCodecs.BOOL, FlightThrustStatePayload::active, FlightThrustStatePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
