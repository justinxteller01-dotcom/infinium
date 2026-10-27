package dev.justin.infinium;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S packet: player configured sword sound theme choices.
 */
public record SwordThemePayload(byte singularity, byte cyberpsycho, byte infinity) implements CustomPacketPayload {
    public static final Type<SwordThemePayload> TYPE = new Type<>(Infinium.id("sword_theme"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SwordThemePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.BYTE, SwordThemePayload::singularity,
        ByteBufCodecs.BYTE, SwordThemePayload::cyberpsycho,
        ByteBufCodecs.BYTE, SwordThemePayload::infinity,
        SwordThemePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
