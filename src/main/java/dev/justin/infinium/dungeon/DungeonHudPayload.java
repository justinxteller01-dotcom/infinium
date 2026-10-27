package dev.justin.infinium.dungeon;

import dev.justin.infinium.Infinium;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DungeonHudPayload(boolean inDungeon, String timeRemaining) implements CustomPacketPayload {
    public static final Type<DungeonHudPayload> TYPE = new Type<>(Infinium.id("dungeon_hud"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonHudPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, DungeonHudPayload::inDungeon,
        ByteBufCodecs.STRING_UTF8, DungeonHudPayload::timeRemaining,
        DungeonHudPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
