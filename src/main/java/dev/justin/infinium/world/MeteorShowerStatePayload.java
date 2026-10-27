package dev.justin.infinium.world;

import dev.justin.infinium.Infinium;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Syncs the current meteor shower state from server to client.
 * Phase:
 *  0 = IDLE
 *  1 = WARNING (2 min out)
 *  2 = COUNTDOWN (12 sec out)
 *  3 = ACTIVE (falling meteors + ambient drone)
 */
public record MeteorShowerStatePayload(int phase, int ticksRemaining) implements CustomPacketPayload {
    public static final Type<MeteorShowerStatePayload> TYPE = new Type<>(Infinium.id("meteor_shower_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MeteorShowerStatePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, MeteorShowerStatePayload::phase,
        ByteBufCodecs.VAR_INT, MeteorShowerStatePayload::ticksRemaining,
        MeteorShowerStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
