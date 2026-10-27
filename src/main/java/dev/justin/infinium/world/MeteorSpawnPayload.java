package dev.justin.infinium.world;

import dev.justin.infinium.Infinium;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Informs nearby clients of a real falling meteor streak.
 */
public record MeteorSpawnPayload(
    int meteorId,
    double startX,
    double startY,
    double startZ,
    double targetX,
    double targetY,
    double targetZ,
    int flightTicks,
    boolean dropsLoot
) implements CustomPacketPayload {
    public static final Type<MeteorSpawnPayload> TYPE = new Type<>(Infinium.id("meteor_spawn"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MeteorSpawnPayload> CODEC = StreamCodec.of(
        (buf, val) -> {
            buf.writeVarInt(val.meteorId());
            buf.writeDouble(val.startX());
            buf.writeDouble(val.startY());
            buf.writeDouble(val.startZ());
            buf.writeDouble(val.targetX());
            buf.writeDouble(val.targetY());
            buf.writeDouble(val.targetZ());
            buf.writeVarInt(val.flightTicks());
            buf.writeBoolean(val.dropsLoot());
        },
        buf -> new MeteorSpawnPayload(
            buf.readVarInt(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readBoolean()
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
