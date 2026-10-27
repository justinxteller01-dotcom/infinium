package dev.justin.infinium.ability;

import dev.justin.infinium.Infinium;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S: player pressed an ability key. The server checks owned/enabled/cooldown. */
public record AbilityActivatePayload(Identifier id) implements CustomPacketPayload {
    public static final Type<AbilityActivatePayload> TYPE = new Type<>(Infinium.id("ability_activate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityActivatePayload> CODEC =
        Identifier.STREAM_CODEC.<RegistryFriendlyByteBuf>cast().map(AbilityActivatePayload::new, AbilityActivatePayload::id);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
