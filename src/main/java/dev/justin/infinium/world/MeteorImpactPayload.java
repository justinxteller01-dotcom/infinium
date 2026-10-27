package dev.justin.infinium.world;

import dev.justin.infinium.Infinium;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Informs nearby clients of a meteor impact explosion and cosmetic blast decal.
 */
public record MeteorImpactPayload(
    double x,
    double y,
    double z,
    boolean dropsLoot
) implements CustomPacketPayload {
    public static final Type<MeteorImpactPayload> TYPE = new Type<>(Infinium.id("meteor_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MeteorImpactPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE, MeteorImpactPayload::x,
        ByteBufCodecs.DOUBLE, MeteorImpactPayload::y,
        ByteBufCodecs.DOUBLE, MeteorImpactPayload::z,
        ByteBufCodecs.BOOL, MeteorImpactPayload::dropsLoot,
        MeteorImpactPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
