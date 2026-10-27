package dev.justin.infinium.loot;

import dev.justin.infinium.Infinium;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C. One player's nearby chests only. kind: 0 none (not sent), 1 fragments, 2 Astral, 3 Cybernetic, 4 Singularity, 5 Cyber Psycho. */
public record ChestHintPayload(List<ChestHintPayload.Hint> hints) implements CustomPacketPayload {
    public record Hint(int x, int y, int z, int kind) {}

    public static final Type<ChestHintPayload> TYPE = new Type<>(Infinium.id("chest_hints"));
    private static final StreamCodec<RegistryFriendlyByteBuf, Hint> HINT_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Hint::x,
            ByteBufCodecs.VAR_INT, Hint::y,
            ByteBufCodecs.VAR_INT, Hint::z,
            ByteBufCodecs.VAR_INT, Hint::kind,
            Hint::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, ChestHintPayload> CODEC = StreamCodec.composite(
            HINT_CODEC.apply(ByteBufCodecs.list(64)), ChestHintPayload::hints, ChestHintPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
