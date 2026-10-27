package dev.justin.infinium;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** M5 S2C: authoritative disabled-perk mask (ack for PerkTogglePayload, also sent when a toggle was rejected). */
public record PerkMaskPayload(long mask) implements CustomPacketPayload {
    public static final Type<PerkMaskPayload> TYPE = new Type<>(Infinium.id("perk_mask"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PerkMaskPayload> CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_LONG, PerkMaskPayload::mask, PerkMaskPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
