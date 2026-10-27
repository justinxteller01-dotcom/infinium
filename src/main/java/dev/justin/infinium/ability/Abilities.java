package dev.justin.infinium.ability;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import com.mojang.serialization.Codec;
import dev.justin.infinium.Infinium;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Ability registry + server-authoritative activation/cooldowns.
 *
 * Register abilities from the COMMON initializer (Infinium.onInitialize, which runs before the client
 * initializer), because the client creates one KeyMapping per ability at client init and key mappings
 * cannot be added after options load. {@link #register} throws once the registry is frozen.
 */
public final class Abilities {
    private static final Map<Identifier, Ability> REGISTRY = new LinkedHashMap<>();
    private static boolean frozen = false;
    private static volatile BiPredicate<Player, Ability> enabledResolver = (p, a) -> true;

    /**
     * M2: cooldown end per ability, as OVERWORLD game time (saved with the world, same value in every dimension).
     * Stored on the player (persistent, copyOnDeath) so cooldowns survive relog, death and dimension changes.
     * Game time only advances while the world runs, so time spent logged out of a stopped server does not count.
     */
    public static final AttachmentType<Map<String, Long>> COOLDOWNS = AttachmentRegistry.create(
        Infinium.id("ability_cooldowns"),
        b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath().initializer(Map::of));

    public static synchronized Ability register(Ability a) {
        if (frozen) throw new IllegalStateException("Infinium ability registered too late (after client key init): " + a.id());
        if (REGISTRY.putIfAbsent(a.id(), a) != null) throw new IllegalArgumentException("Duplicate ability " + a.id());
        return a;
    }

    public static synchronized void freeze() { frozen = true; }
    public static Ability get(Identifier id) { return REGISTRY.get(id); }
    public static List<Ability> all() { return Collections.unmodifiableList(List.copyOf(REGISTRY.values())); }

    /** M5 hook: settings/matrix decide if an ability is enabled. Must be side-safe (client + server). */
    public static void setEnabledResolver(BiPredicate<Player, Ability> r) { enabledResolver = r == null ? (p, a) -> true : r; }
    static BiPredicate<Player, Ability> enabledResolver() { return enabledResolver; }

    public static void registerNetworking() {
        PayloadTypeRegistry.serverboundPlay().register(AbilityActivatePayload.TYPE, AbilityActivatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AbilityCooldownPayload.TYPE, AbilityCooldownPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AbilityActivatePayload.TYPE, (payload, ctx) -> {
            ServerPlayer sp = ctx.player();
            ctx.server().execute(() -> tryActivate(sp, payload.id()));
        });
        ServerPlayConnectionEvents.JOIN.register((h, sender, srv) -> syncAll(h.getPlayer()));   // M2: resync saved cooldowns
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> syncAll(newP));
    }

    private static long now(ServerPlayer p) { return p.level().getServer().overworld().getGameTime(); }
    private static Map<String, Long> map(ServerPlayer p) {
        Map<String, Long> m = ((AttachmentTarget) (Object) p).getAttached(COOLDOWNS);
        return m == null ? Map.of() : m;
    }

    /** Remaining cooldown ticks on the server (0 = ready). */
    public static int remaining(ServerPlayer p, Identifier id) {
        Long end = map(p).get(id.toString());
        return end == null ? 0 : (int) Math.max(0, Math.min(Integer.MAX_VALUE, end - now(p)));
    }

    /** Sends every running cooldown to the client and drops expired / unknown entries. */
    public static void syncAll(ServerPlayer p) {
        Map<String, Long> kept = new HashMap<>();
        for (Map.Entry<String, Long> e : map(p).entrySet()) {
            Identifier id = Identifier.tryParse(e.getKey());
            Ability a = id == null ? null : REGISTRY.get(id);
            int left = (int) Math.max(0, Math.min(Integer.MAX_VALUE, e.getValue() - now(p)));
            if (a == null || left <= 0) continue;
            kept.put(e.getKey(), e.getValue());
            ServerPlayNetworking.send(p, new AbilityCooldownPayload(id, left, Math.max(left, a.cooldownTicks().applyAsInt(p)), false));
        }
        ((AttachmentTarget) (Object) p).setAttached(COOLDOWNS, kept);
    }

    static void tryActivate(ServerPlayer p, Identifier id) {
        Ability a = REGISTRY.get(id);
        if (a == null || !p.isAlive() || p.isSpectator()) return;
        if (!a.owned().test(p) || !a.enabled(p)) return;
        int left = remaining(p, id);
        if (left > 0) {   // client was out of sync (or spamming): resync, do nothing
            ServerPlayNetworking.send(p, new AbilityCooldownPayload(id, left, Math.max(left, a.cooldownTicks().applyAsInt(p)), false));
            return;
        }
        if (!a.action().test(p)) return;   // not applicable right now: no cooldown spent
        startCooldown(p, a);
    }

    /** Starts the cooldown server-side and syncs it to the client. Public so abilities with custom triggers can use it. */
    public static void startCooldown(ServerPlayer p, Ability a) {
        int cd = Math.max(0, a.cooldownTicks().applyAsInt(p));
        Map<String, Long> m = new HashMap<>(map(p));   // attachment maps from the codec are immutable: copy
        m.put(a.id().toString(), now(p) + cd);
        ((AttachmentTarget) (Object) p).setAttached(COOLDOWNS, m);
        ServerPlayNetworking.send(p, new AbilityCooldownPayload(a.id(), cd, cd, true));
    }

    private Abilities() {}
}
