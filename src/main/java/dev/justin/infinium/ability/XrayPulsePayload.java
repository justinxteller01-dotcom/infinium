package dev.justin.infinium.ability;

import dev.justin.infinium.Infinium;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C, only to the pulsing player: scanned blocks (packed pos, kind, ARGB) and entity ids to follow. */
public record XrayPulsePayload(int radius, List<Hit> blocks, List<Mob> mobs) implements CustomPacketPayload {
    public record Hit(long pos, byte kind, int color) {}
    public record Mob(int entityId, byte kind) {}

    private static final StreamCodec<RegistryFriendlyByteBuf, Hit> HIT = StreamCodec.composite(
        ByteBufCodecs.LONG, Hit::pos, ByteBufCodecs.BYTE, Hit::kind, ByteBufCodecs.INT, Hit::color, Hit::new);
    private static final StreamCodec<RegistryFriendlyByteBuf, Mob> MOB = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, Mob::entityId, ByteBufCodecs.BYTE, Mob::kind, Mob::new);
    public static final Type<XrayPulsePayload> TYPE = new Type<>(Infinium.id("xray_pulse"));
    public static final StreamCodec<RegistryFriendlyByteBuf, XrayPulsePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, XrayPulsePayload::radius,
        HIT.apply(ByteBufCodecs.list(XrayPulse.MAX_BLOCKS)), XrayPulsePayload::blocks,
        MOB.apply(ByteBufCodecs.list(XrayPulse.MAX_ENTITIES)), XrayPulsePayload::mobs,
        XrayPulsePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
