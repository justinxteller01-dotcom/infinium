package dev.justin.infinium;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S perk toggle (13.14): mode 0 = off, 1 = on, 3 = set every family's perk of the same kind to "on" (byte value of on in bit 0 of mode 2|on). */
public record PerkTogglePayload(int perk, byte mode) implements CustomPacketPayload {
    public static final Type<PerkTogglePayload> TYPE = new Type<>(Infinium.id("perk_toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PerkTogglePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, PerkTogglePayload::perk, ByteBufCodecs.BYTE, PerkTogglePayload::mode, PerkTogglePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
