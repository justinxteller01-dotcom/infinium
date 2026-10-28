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
 * Strictly server-authoritative: enforces rate limits, 1.25x stab gate, server player's own rotation,
 * and clamps color. Contains NO client-only classes or imports.
 */
public final class SwordSlashHandler {
    private static final Map<UUID, Long> LAST_SERVER_SLASH_TICK = new ConcurrentHashMap<>();

    private SwordSlashHandler() {}

    /** Server side: processes C2S SwordSlashPayload to spawn EnergySlashEntity. */
    public static void handleServerSlash(ServerPlayer player, float clientYaw, float clientPitch, float roll, int color, boolean isStab) {
        if (!player.isAlive()) return;
        ItemStack held = player.getMainHandItem();
        if (!held.is(Infinium.SWORD) || InfiniumDurability.disabled(held)) return;
        if (!InfiniumSwordItem.isEnergyMode(held)) return;
        int tier = InfiniumArmorTier.getTier(player);
        if (tier < 1 || !CrystalCharge.saberUnlocked(player)) return;

        double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        int baseInterval = Math.max(3, (int) Math.floor(20.0 / attackSpeed) - 1);
        int minIntervalTicks = isStab ? (int) Math.round(baseInterval * 1.25) : baseInterval;

        long now = player.level().getGameTime();
        Long last = LAST_SERVER_SLASH_TICK.get(player.getUUID());
        if (last != null && (now - last) < minIntervalTicks) return;
        LAST_SERVER_SLASH_TICK.put(player.getUUID(), now);

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

        // Broadcast release audio to nearby players (volume & pitch scaled by armor tier)
        float vol = tier == 1 ? 0.60F : (tier == 2 ? 0.85F : 1.0F);
        float pitch = tier == 1 ? 0.90F : (tier == 2 ? 1.0F : 1.10F);
        if (isStab) {
            Holder<SoundEvent> s = ModSounds.sword(player, ModSounds.STAB_LAUNCH, ModSounds.SWORD_STAB_LAUNCH);
            if (s != null) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    s.value(), SoundSource.PLAYERS, vol, pitch);
            }
        } else {
            Holder<SoundEvent> s = ModSounds.sword(player, ModSounds.SLASH_LAUNCH, ModSounds.SWORD_SLASH_LAUNCH);
            if (s != null) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    s.value(), SoundSource.PLAYERS, vol, pitch);
            }
        }
    }

    /** Clears per-player rate-limit timestamp when a player disconnects to prevent memory leaks. */
    public static void onPlayerDisconnect(UUID uuid) {
        if (uuid != null) {
            LAST_SERVER_SLASH_TICK.remove(uuid);
        }
    }
}
