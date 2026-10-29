package dev.justin.infinium;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side authoritative handler for Infinium Sword energy slashes.
 * Strictly server-authoritative: enforces rate limits (with packet slack), the post-stab twirl lockout, server player's own rotation,
 * and clamps color. Contains NO client-only classes or imports.
 */
public final class SwordSlashHandler {
    private static final Map<UUID, Long> LAST_SERVER_SLASH_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LOCK_UNTIL_TICK = new ConcurrentHashMap<>();

    private SwordSlashHandler() {}

    /** Server side: processes C2S SwordSlashPayload to spawn EnergySlashEntity. */
    public static void handleServerSlash(ServerPlayer player, float clientYaw, float clientPitch, float roll, int color, boolean isStab) {
        if (!player.isAlive()) return;
        ItemStack held = player.getMainHandItem();
        if (!held.is(Infinium.SWORD) || InfiniumDurability.disabled(held)) return;
        if (!InfiniumSwordItem.isEnergyMode(held)) return;
        int tier = InfiniumArmorTier.getTier(player);
        if (tier < 1 || !CrystalCharge.saberUnlocked(player)) return;

        // Rate limit with slack: packets sent N client ticks apart can arrive fewer server ticks apart
        // (network bunching). The old exact 1.25x stab gate silently dropped correctly-timed stabs.
        long now = player.level().getGameTime();
        UUID id = player.getUUID();
        Long last = LAST_SERVER_SLASH_TICK.get(id);
        int minInterval = Math.max(1, SwordCombo.minIntervalTicks(player) - SwordCombo.SERVER_SLACK_TICKS);
        if (last != null && (now - last) < minInterval) return;
        Long lock = LOCK_UNTIL_TICK.get(id);
        if (lock != null && now < lock) return;   // post-stab twirl cooldown
        LAST_SERVER_SLASH_TICK.put(id, now);
        if (isStab) LOCK_UNTIL_TICK.put(id, now + Math.max(0, SwordCombo.postStabLockTicks(player) - SwordCombo.SERVER_SLACK_TICKS));

        // Server authoritative rotation and clamped color
        float serverYaw = player.getYRot();
        float serverPitch = player.getXRot();
        int clampedColor = color & 0xFFFFFF;

        // Calculate trajectory vector using server player's own rotation
        float f = -Mth.sin(serverYaw * ((float)Math.PI / 180F)) * Mth.cos(serverPitch * ((float)Math.PI / 180F));
        float g = -Mth.sin(serverPitch * ((float)Math.PI / 180F));
        float h = Mth.cos(serverYaw * ((float)Math.PI / 180F)) * Mth.cos(serverPitch * ((float)Math.PI / 180F));
        Vec3 dir = new Vec3(f, g, h).normalize();

        Vec3 spawnPos = player.getEyePosition().add(dir.scale(0.6));
        if (Float.isNaN(roll) || Float.isInfinite(roll)) return;
        float clampedRoll = Mth.clamp(roll, -180.0F, 180.0F);

        EnergySlashEntity slash = new EnergySlashEntity(Infinium.ENERGY_SLASH, player.level());
        slash.setOwner(player);
        slash.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        slash.setYRot(serverYaw);
        slash.setXRot(serverPitch);
        slash.setRoll(clampedRoll);
        slash.setColor(clampedColor);
        slash.setStab(isStab);
        slash.setTier(tier);
        slash.setDeltaMovement(dir.scale(2.2)); // Laser velocity ~44 blocks/sec

        player.level().addFreshEntity(slash);

        // Release audio for OTHER nearby players (the shooter already heard it client-side, instantly).
        // Combo step from the payload: stab = step 3, roll +35 = right slash (step 2), else step 1.
        float vol = tier == 1 ? 0.75F : (tier == 2 ? 0.9F : 1.0F);
        int kind = isStab ? ModSounds.STAB_LAUNCH : (clampedRoll >= 10F && clampedRoll < 60F ? ModSounds.SLASH2_LAUNCH : ModSounds.SLASH_LAUNCH);
        Holder<SoundEvent> s = ModSounds.sword(player, kind, isStab ? ModSounds.SWORD_STAB_LAUNCH : ModSounds.SWORD_SLASH_LAUNCH);
        if (s != null) {
            player.level().playSound(player, player.getX(), player.getY(), player.getZ(), s.value(), SoundSource.PLAYERS, vol, 1.0F);
        }
    }

    /** Clears per-player rate-limit timestamp when a player disconnects to prevent memory leaks. */
    public static void onPlayerDisconnect(UUID uuid) {
        if (uuid != null) {
            LAST_SERVER_SLASH_TICK.remove(uuid);
            LOCK_UNTIL_TICK.remove(uuid);
        }
    }
}
