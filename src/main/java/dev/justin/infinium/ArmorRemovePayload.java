package dev.justin.infinium;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** R4 C2S: the player confirmed "Remove" on the armor-removal warning. hand >= 0: hotbar equip swap; else a container input. */
public record ArmorRemovePayload(int containerId, int slot, int button, int input, int hand) implements CustomPacketPayload {
    public static final Type<ArmorRemovePayload> TYPE = new Type<>(Infinium.id("armor_remove"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorRemovePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ArmorRemovePayload::containerId, ByteBufCodecs.VAR_INT, ArmorRemovePayload::slot,
        ByteBufCodecs.VAR_INT, ArmorRemovePayload::button, ByteBufCodecs.VAR_INT, ArmorRemovePayload::input,
        ByteBufCodecs.VAR_INT, ArmorRemovePayload::hand, ArmorRemovePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
