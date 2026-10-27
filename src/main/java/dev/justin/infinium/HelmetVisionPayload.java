package dev.justin.infinium;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S packet: player toggled helmet vision mode via hotkey or on-screen button.
 * visionType: 0 = X-Ray Vision, 1 = Full Bright.
 */
public record HelmetVisionPayload(byte visionType, boolean active) implements CustomPacketPayload {
    public static final Type<HelmetVisionPayload> TYPE = new Type<>(Infinium.id("helmet_vision"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HelmetVisionPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.BYTE, HelmetVisionPayload::visionType,
        ByteBufCodecs.BOOL, HelmetVisionPayload::active,
        HelmetVisionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
