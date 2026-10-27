package dev.justin.infinium;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: toggle the built-in glider on an Infinium chestplate. invIndex >= 0: player Inventory index; else menu slot. */
public record ToggleGlidePayload(int containerId, int slot, int invIndex) implements CustomPacketPayload {
    public static final Type<ToggleGlidePayload> TYPE = new Type<>(Infinium.id("toggle_glide"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleGlidePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ToggleGlidePayload::containerId,
        ByteBufCodecs.VAR_INT, ToggleGlidePayload::slot,
        ByteBufCodecs.VAR_INT, ToggleGlidePayload::invIndex,
        ToggleGlidePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
