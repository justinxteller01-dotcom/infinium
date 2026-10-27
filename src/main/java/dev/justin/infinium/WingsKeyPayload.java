package dev.justin.infinium;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: the custom wings key was pressed (13.15). The server toggles the worn chestplate (rate-limited). */
public record WingsKeyPayload() implements CustomPacketPayload {
    public static final Type<WingsKeyPayload> TYPE = new Type<>(Infinium.id("wings_key"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WingsKeyPayload> CODEC = StreamCodec.unit(new WingsKeyPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
