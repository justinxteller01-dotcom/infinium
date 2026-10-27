package dev.justin.infinium.ability;

import dev.justin.infinium.Infinium;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C: authoritative cooldown for one ability. remaining/total in ticks (total drives the sweep).
 *  started = true only for the packet sent when the ability just fired (client-side follow-up effects, e.g. warp sprint). */
public record AbilityCooldownPayload(Identifier id, int remaining, int total, boolean started) implements CustomPacketPayload {
    public static final Type<AbilityCooldownPayload> TYPE = new Type<>(Infinium.id("ability_cooldown"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityCooldownPayload> CODEC = StreamCodec.composite(
        Identifier.STREAM_CODEC.cast(), AbilityCooldownPayload::id,
        ByteBufCodecs.VAR_INT, AbilityCooldownPayload::remaining,
        ByteBufCodecs.VAR_INT, AbilityCooldownPayload::total,
        ByteBufCodecs.BOOL, AbilityCooldownPayload::started,
        AbilityCooldownPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
