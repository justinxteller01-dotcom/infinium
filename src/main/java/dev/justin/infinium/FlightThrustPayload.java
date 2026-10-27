package dev.justin.infinium;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: player is holding or released the rebindable flight thrust key while airborne. */
public record FlightThrustPayload(boolean active) implements CustomPacketPayload {
    public static final Type<FlightThrustPayload> TYPE = new Type<>(Infinium.id("flight_thrust"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlightThrustPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, FlightThrustPayload::active, FlightThrustPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
