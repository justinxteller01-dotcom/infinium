package dev.justin.infinium.client;

import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfinityFlight;
import dev.justin.infinium.ModSounds;
import dev.justin.infinium.Perk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side input and audio helper for Infinium rocket-free elytra flight.
 */
public final class InfinityFlightClient {
    private static FlightThrustSoundInstance activeThrustSound = null;
    private static boolean wasFallFlying = false;

    // M6: synced thrust state of other players (entity id -> holding), fed by FlightThrustStatePayload.
    private static final java.util.Set<Integer> REMOTE_THRUST = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Map<Integer, FlightThrustSoundInstance> REMOTE_SOUND = new java.util.HashMap<>();
    private static final java.util.Set<Integer> REMOTE_FLYING = new java.util.HashSet<>();

    public static void register() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(dev.justin.infinium.FlightThrustStatePayload.TYPE,
            (p, ctx) -> { if (p.active()) REMOTE_THRUST.add(p.entityId()); else REMOTE_THRUST.remove(p.entityId()); });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(() -> {
            REMOTE_THRUST.clear(); REMOTE_FLYING.clear(); REMOTE_SOUND.clear(); }));
    }

    public static boolean isRemoteThrusting(Player p) { return p != null && p.isFallFlying() && REMOTE_THRUST.contains(p.getId()); }

    /** Local player: the key; anyone else: synced state. */
    public static boolean thrusting(Player p) { return p instanceof LocalPlayer ? isHoldingThrust(p) : isRemoteThrusting(p); }

    public static boolean isHoldingThrust(Player player) {
        if (!(player instanceof LocalPlayer)) return false;
        return InfiniumKeys.isThrustHeld();
    }

    public static boolean isHoldingForward(Player player) {
        return isHoldingThrust(player);
    }

    public static void clientTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) {
            wasFallFlying = false;
            if (activeThrustSound != null) {
                activeThrustSound.stopFlightSound();
                activeThrustSound = null;
            }
            return;
        }

        remoteTick(mc, player);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean thrustFlight = InfinityFlight.grantsThrustFlight(chest);
        boolean fallFlying = player.isFallFlying();
        int fam = Math.max(0, Math.min(4, Perk.family(chest)));

        // Flight enter cue: rising edge of fall flying with qualifying chestplate
        if (thrustFlight && fallFlying && !wasFallFlying) {
            Holder<SoundEvent> enterCue = ModSounds.flightEnter(fam);
            if (enterCue != null && enterCue.value() != null) {
                float vol = 0.85F * InfiniumClientConfig.soundVolumeMaster();
                mc.getSoundManager().play(new SimpleSoundInstance(
                    enterCue.value(), SoundSource.PLAYERS, vol, 1.0F,
                    SoundInstance.createUnseededRandom(), player.getX(), player.getY(), player.getZ()));
            }
        }
        wasFallFlying = fallFlying;

        // Continuous flight thrust sound while holding the Flight Thrust key (default Left Ctrl)
        if (thrustFlight && fallFlying && isHoldingThrust(player)) {
            if (activeThrustSound == null || activeThrustSound.isStopped()) {
                Holder<SoundEvent> loopEvent = ModSounds.flightThrust(fam);
                if (loopEvent != null && loopEvent.value() != null) {
                    activeThrustSound = new FlightThrustSoundInstance(player, fam, loopEvent.value());
                    mc.getSoundManager().play(activeThrustSound);
                }
            }
        } else {
            if (activeThrustSound != null) {
                activeThrustSound.stopFlightSound();
                activeThrustSound = null;
            }
        }
    }

    /** M6: flight enter cue + thrust loop for nearby players (positional; normal sound attenuation decides who hears it). */
    private static void remoteTick(Minecraft mc, LocalPlayer self) {
        java.util.Set<Integer> live = new java.util.HashSet<>();
        for (Player o : mc.level.players()) {
            if (o == self) continue;
            int id = o.getId();
            ItemStack chest = o.getItemBySlot(EquipmentSlot.CHEST);
            boolean ok = InfinityFlight.grantsThrustFlight(chest) && o.isFallFlying();
            if (!o.isFallFlying()) REMOTE_THRUST.remove(id);
            int fam = Math.max(0, Math.min(4, Perk.family(chest)));
            if (ok) {
                live.add(id);
                if (REMOTE_FLYING.add(id)) {
                    Holder<SoundEvent> cue = ModSounds.flightEnter(fam);
                    if (cue != null) mc.getSoundManager().play(new SimpleSoundInstance(cue.value(), SoundSource.PLAYERS,
                        0.85F * InfiniumClientConfig.soundVolumeMaster(), 1.0F, SoundInstance.createUnseededRandom(), o.getX(), o.getY(), o.getZ()));
                }
            }
            FlightThrustSoundInstance si = REMOTE_SOUND.get(id);
            if (ok && REMOTE_THRUST.contains(id)) {
                if (si == null || si.isStopped()) {
                    Holder<SoundEvent> loop = ModSounds.flightThrust(fam);
                    if (loop != null) { si = new FlightThrustSoundInstance(o, fam, loop.value()); REMOTE_SOUND.put(id, si); mc.getSoundManager().play(si); }
                }
            } else if (si != null) { si.stopFlightSound(); REMOTE_SOUND.remove(id); }
        }
        REMOTE_FLYING.retainAll(live);
        REMOTE_SOUND.entrySet().removeIf(e -> { if (!live.contains(e.getKey())) { e.getValue().stopFlightSound(); return true; } return false; });
    }

    private InfinityFlightClient() {}
}
