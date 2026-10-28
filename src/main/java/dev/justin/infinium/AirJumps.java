package dev.justin.infinium;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * G4b air jumps (Justin 2026-10-27 11:43/11:46): the Cybernetic / Cyber Psycho / Infinity set Jump perk gives 1 / 2 / 3
 * mid-air jumps (config air_jumps_t1..t3, M5 toggle). The client predicts the jump and sends {@link Use}; the server
 * checks the remaining count, resets fall distance and plays the sound for everyone else. Landing refills. A successful
 * Infinity warp refills to the full count and sends {@link Sync} (an absolute value, so the client cannot double-count).
 */
public final class AirJumps {
    /** C2S: one air jump was used. */
    public record Use() implements CustomPacketPayload {
        public static final Type<Use> TYPE = new Type<>(Infinium.id("air_jump"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Use> CODEC = StreamCodec.unit(new Use());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    /** S2C: set the client's remaining air jumps to this value. */
    public record Sync(int remaining) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(Infinium.id("air_jump_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, Sync::remaining, Sync::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static final Map<UUID, Integer> REMAINING = new HashMap<>();

    public static int max(Player p) { return SetBonuses.airJumps(p); }

    /** Not on the ground, not in water or lava, not fall-flying, not on a ladder, not flying or riding. */
    public static boolean canAirJump(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.onGround() && !p.isInWater() && !p.isInLava() && !p.isFallFlying()
            && !p.onClimbable() && !p.getAbilities().flying && !p.isPassenger() && !elytraOn(p);
    }

    /** Batch1 (Justin): air jumps only with the Elytra toggled OFF. Toggled on (chest has GLIDER) = jump in air opens the elytra as vanilla. */
    public static boolean elytraOn(Player p) {
        return p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).has(net.minecraft.core.component.DataComponents.GLIDER);
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Use.TYPE, Use.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Use.TYPE, (pl, ctx) -> ctx.server().execute(() -> use(ctx.player())));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) if (p.onGround()) REMAINING.remove(p.getUUID());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> REMAINING.remove(h.getPlayer().getUUID()));
    }

    static void use(ServerPlayer p) {
        int max = max(p);
        int left = REMAINING.getOrDefault(p.getUUID(), max);
        if (left > max) left = max;
        if (max <= 0 || left <= 0 || !canAirJump(p)) {
            ServerPlayNetworking.send(p, new Sync(Math.max(0, left)));   // rejected: correct the client
            return;
        }
        left--;
        REMAINING.put(p.getUUID(), left);
        p.resetFallDistance();
        // Positional for everyone else; the jumper already heard the client-predicted copy (except = p).
        p.level().playSound(p, p.getX(), p.getY(), p.getZ(), (left == 0 ? ModSounds.BONUS_JUMP_LAST : ModSounds.BONUS_JUMP).value(),
            SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    /** Successful warp: Infinity set refills its air jumps (not the ground jump). */
    public static void onWarp(ServerPlayer p) {
        if (SetLook.of(p) != SetLook.INFINITY) return;
        int max = max(p);
        if (max <= 0) return;
        REMAINING.put(p.getUUID(), max);
        ServerPlayNetworking.send(p, new Sync(max));
    }

    private AirJumps() {}
}
