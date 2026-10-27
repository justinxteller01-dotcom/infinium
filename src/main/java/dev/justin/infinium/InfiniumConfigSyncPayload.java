package dev.justin.infinium;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: Syncs server-side configuration JSON to connecting clients. */
public record InfiniumConfigSyncPayload(String json) implements CustomPacketPayload {
    public static final Type<InfiniumConfigSyncPayload> TYPE = new Type<>(Infinium.id("config_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InfiniumConfigSyncPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, InfiniumConfigSyncPayload::json,
        InfiniumConfigSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
