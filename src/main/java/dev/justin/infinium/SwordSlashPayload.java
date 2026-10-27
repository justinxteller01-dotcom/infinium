package dev.justin.infinium;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S packet: player swung Infinium Sword at 100% full attack charge.
 * Transmits trajectory angles, roll orientation, active dye/style color, and whether this is a stab beam.
 */
public record SwordSlashPayload(float yaw, float pitch, float roll, int color, boolean isStab) implements CustomPacketPayload {
    public static final Type<SwordSlashPayload> TYPE = new Type<>(Infinium.id("sword_slash"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SwordSlashPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, SwordSlashPayload::yaw,
        ByteBufCodecs.FLOAT, SwordSlashPayload::pitch,
        ByteBufCodecs.FLOAT, SwordSlashPayload::roll,
        ByteBufCodecs.VAR_INT, SwordSlashPayload::color,
        ByteBufCodecs.BOOL, SwordSlashPayload::isStab,
        SwordSlashPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
