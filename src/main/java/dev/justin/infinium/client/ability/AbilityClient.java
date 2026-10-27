package dev.justin.infinium.client.ability;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.blaze3d.platform.InputConstants;
import dev.justin.infinium.ability.Abilities;
import dev.justin.infinium.ability.Ability;
import dev.justin.infinium.ability.AbilityActivatePayload;
import dev.justin.infinium.ability.AbilityCooldownPayload;
import dev.justin.infinium.client.InfiniumKeys;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * Client side of the ability system: one UNBOUND KeyMapping per ability (Infinium category), the
 * obtained-order list (persisted to config/infinium_ability_order.json) and the synced cooldown state.
 */
public final class AbilityClient {
    private static final Map<Identifier, KeyMapping> KEYS = new LinkedHashMap<>();
    /** ids in the order first obtained; never reordered, never removed (an ability you take off keeps its slot). */
    private static final List<Identifier> ORDER = new ArrayList<>();
    private static final Map<Identifier, Cooldown> COOLDOWNS = new HashMap<>();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("infinium_ability_order.json");

    /** endNanos = System.nanoTime() when ready; totalTicks drives the sweep fraction. */
    record Cooldown(long endNanos, int totalTicks) {
        float remainingTicks() { return Math.max(0, (endNanos - System.nanoTime()) / 50_000_000F); }
    }

    private static final Map<Identifier, java.util.function.Consumer<Player>> ON_FIRED = new HashMap<>();

    /** Client follow-up when the server confirms an ability fired (runs on the client thread). */
    public static void onFired(Identifier id, java.util.function.Consumer<Player> fx) { ON_FIRED.put(id, fx); }

    public static void register() {
        Abilities.freeze();
        for (Ability a : Abilities.all()) {
            KEYS.put(a.id(), KeyMappingHelper.registerKeyMapping(new KeyMapping(
                a.keyName(), InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), InfiniumKeys.CATEGORY)));   // DEFAULT UNBOUND
        }
        load();
        // M2: come out of a warp already sprinting (same effect as pressing Sprint right after; vanilla drops it
        // again on its own if forward isn't held, exactly like a real sprint key press).
        onFired(dev.justin.infinium.SpacetimeWarp.ID, pl -> pl.setSprinting(true));

        ClientPlayNetworking.registerGlobalReceiver(AbilityCooldownPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            if (p.started()) {
                java.util.function.Consumer<Player> fx = ON_FIRED.get(p.id());
                if (fx != null && ctx.client().player != null) fx.accept(ctx.client().player);
            }
            if (p.remaining() <= 0) COOLDOWNS.remove(p.id());
            else COOLDOWNS.put(p.id(), new Cooldown(System.nanoTime() + p.remaining() * 50_000_000L, Math.max(1, p.total())));
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> COOLDOWNS.clear());

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Player pl = mc.player;
            if (pl == null) { KEYS.values().forEach(k -> { while (k.consumeClick()) {} }); return; }
            trackOwned(pl);
            for (Ability a : Abilities.all()) {
                KeyMapping k = KEYS.get(a.id());
                while (k.consumeClick()) {
                    if (mc.gui.screen() != null) continue;
                    if (!a.owned().test(pl) || !a.enabled(pl) || isCoolingDown(a.id())) continue;
                    if (ClientPlayNetworking.canSend(AbilityActivatePayload.TYPE)) ClientPlayNetworking.send(new AbilityActivatePayload(a.id()));
                }
            }
        });
    }

    /** Appends newly owned abilities; several gained in the same tick are shuffled (keybinds are per ability, so they never change). */
    private static void trackOwned(Player pl) {
        List<Identifier> fresh = null;
        for (Ability a : Abilities.all()) {
            if (!ORDER.contains(a.id()) && a.owned().test(pl)) {
                if (fresh == null) fresh = new ArrayList<>();
                fresh.add(a.id());
            }
        }
        if (fresh == null) return;
        Collections.shuffle(fresh);
        ORDER.addAll(fresh);
        save();
    }

    /** Owned + enabled abilities in display order. */
    public static List<Ability> visible(Player pl) {
        List<Ability> out = new ArrayList<>();
        for (Identifier id : ORDER) {
            Ability a = Abilities.get(id);
            if (a != null && a.owned().test(pl) && a.enabled(pl)) out.add(a);
        }
        return out;
    }

    public static KeyMapping key(Ability a) { return KEYS.get(a.id()); }

    public static boolean isCoolingDown(Identifier id) { return remainingTicks(id) > 0; }

    public static float remainingTicks(Identifier id) {
        Cooldown c = COOLDOWNS.get(id);
        return c == null ? 0 : c.remainingTicks();
    }

    /** 0 = just used, 1 = ready. */
    public static float progress(Identifier id) {
        Cooldown c = COOLDOWNS.get(id);
        if (c == null) return 1F;
        float r = c.remainingTicks();
        return r <= 0 ? 1F : 1F - Math.min(1F, r / c.totalTicks());
    }

    private static void load() {
        try {
            if (!Files.exists(FILE)) return;
            JsonArray arr = new Gson().fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonArray.class);
            if (arr == null) return;
            for (JsonElement e : arr) {
                Identifier id = Identifier.tryParse(e.getAsString());
                if (id != null && !ORDER.contains(id)) ORDER.add(id);
            }
        } catch (Exception e) {
            dev.justin.infinium.Infinium.LOG.warn("Could not read {}: {}", FILE, e.toString());
        }
    }

    private static void save() {
        try {
            JsonArray arr = new JsonArray();
            ORDER.forEach(id -> arr.add(id.toString()));
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, arr.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            dev.justin.infinium.Infinium.LOG.warn("Could not write {}: {}", FILE, e.toString());
        }
    }

    private AbilityClient() {}
}
