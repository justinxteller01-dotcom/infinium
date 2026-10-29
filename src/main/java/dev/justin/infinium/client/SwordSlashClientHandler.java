package dev.justin.infinium.client;

import dev.justin.infinium.GlowColor;
import dev.justin.infinium.InfiniumArmorTier;
import dev.justin.infinium.InfiniumStyle;
import dev.justin.infinium.InfiniumSwordItem;
import dev.justin.infinium.ModSounds;
import dev.justin.infinium.SwordSlashPayload;
import dev.justin.infinium.compat.punchy.PunchyBridge;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import punchy.client.animation.data.AnimationClip;
import punchy.config.PunchyTuningConfig;
import java.util.Optional;

/**
 * Client-side handler for Infinium Sword combo mechanics, Punchy animation synchronization,
 * sound design, and projectile dispatch.
 *
 * Combo Pattern:
 * - Stage 0: Left Cut (-35° diagonal, 50% damage, attack_1) -> next stage 1
 * - Stage 1: Right Cut (+35° diagonal, 50% damage, attack_2) -> next stage 2
 * - Stage 2: Stab (Straight laser lance, 75% damage, attack_3, railgun audio) -> next stage 0
 * - (2026-10-29, Infinium animator path) a fully charged third click within SwordCombo.stabWindowTicks (40 ticks,
 *   shrinking slightly with attack speed) of the second swing is ALWAYS the stab. Early click = plain swing + reset.
 * - After the stab: twirl animation; the combo is locked for SwordCombo.postStabLockTicks. No twirl / ready sounds.
 * - The Punchy-clip path below is only used when Infinium does NOT override Punchy (OVERRIDE_PUNCHY=false).
 * - Critical Hit (attack_critical): plays Punchy's firstspinv2 twirl audio synchronized to the itemgrip twirl at 833ms.
 */
public final class SwordSlashClientHandler {
    // Client combo state tracking
    private static int CLIENT_COMBO_STAGE = 0; // 0=Left, 1=Right, 2=Stab
    private static long CLIENT_LAST_SWING_TIME = 0L;
    private static long CLIENT_LAST_SLASH_TICK = -1L;
    private static boolean CLIENT_PENDING_SPIN = false;
    private static long CLIENT_STAB_FINISH_TIME = 0L;
    private static boolean QUEUED_ATTACK_TARGET_BLOCK = false;
    private static long CLIENT_LOCK_UNTIL_TICK = Long.MIN_VALUE;

    // Crit twirl timing (attack_critical itemgrip twirl 0.8333s-1.125s)
    private static final long CRIT_TWIRL_START_MS = 833L;
    private static boolean CLIENT_PENDING_CRIT_TWIRL = false;
    private static long CLIENT_CRIT_START_TIME = 0L;

    private static final Identifier PUNCHY_SPIN_SOUND_ID = Identifier.fromNamespaceAndPath("punchy", "animationssounds/firstspinv2");
    private static SoundInstance ACTIVE_SPIN_SOUND = null;

    public record PunchyAttackResult(AnimationClip clip, int nextStage) {}

    private SwordSlashClientHandler() {}

    public static void setQueuedAttackTargetBlock(boolean isBlock) {
        QUEUED_ATTACK_TARGET_BLOCK = isBlock;
    }

    public static boolean isQueuedAttackTargetBlock() {
        return QUEUED_ATTACK_TARGET_BLOCK;
    }

    /** Looks up Punchy's spin sound event by registry name; returns null if Punchy is absent or sound unregistered. */
    private static SoundEvent getPunchySpinSoundEvent() {
        try {
            return BuiltInRegistries.SOUND_EVENT.getOptional(PUNCHY_SPIN_SOUND_ID).orElse(null);
        } catch (Throwable ignored) {}
        return null;
    }

    /** Stops any active spin sound instance via the Minecraft sound manager. */
    public static void stopActiveSpinSound() {
        if (ACTIVE_SPIN_SOUND != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                try {
                    mc.getSoundManager().stop(ACTIVE_SPIN_SOUND);
                } catch (Throwable ignored) {}
            }
            ACTIVE_SPIN_SOUND = null;
        }
    }

    /** Cancels any pending post-stab/crit flourish twirl and stops active spin sound instance on any attack click. */
    public static void cancelPendingSpin() {
        CLIENT_PENDING_SPIN = false;
        CLIENT_PENDING_CRIT_TWIRL = false;
        stopActiveSpinSound();
    }

    public static int getClientComboStage() {
        return CLIENT_COMBO_STAGE;
    }

    public static int getBaseIntervalTicks(Player player) {
        double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        return Math.max(3, (int) Math.floor(20.0 / attackSpeed) - 1);
    }

    public static int getStabIntervalTicks(Player player) {
        return (int) Math.round(getBaseIntervalTicks(player) * 1.25);
    }

    /** Returns true if energy mode is active, tier >= 1, and rate limit has elapsed. */
    public static boolean canFireSlash(Player player) {
        if (player == null) return false;
        ItemStack held = player.getMainHandItem();
        if (!held.is(dev.justin.infinium.Infinium.SWORD) || dev.justin.infinium.InfiniumDurability.disabled(held)) return false;
        if (!InfiniumSwordItem.isEnergyMode(held) || InfiniumArmorTier.getTier(player) < 1 || !dev.justin.infinium.CrystalCharge.saberUnlocked(player)) return false;
        long currentTick = player.level().getGameTime();
        int baseInterval = getBaseIntervalTicks(player);
        return CLIENT_LAST_SLASH_TICK == -1L || (currentTick - CLIENT_LAST_SLASH_TICK) >= baseInterval;
    }

    /** Resolves active color from dye component or armor/tool style. */
    public static int resolveSwordColor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0xB04CFF;
        int argb = GlowColor.argb(stack);
        if (argb != -1) return argb & 0xFFFFFF;

        InfiniumStyle style = InfiniumStyle.get(stack);
        if (style != null) {
            return switch (style) {
                case ASTRAL -> 0xBD45FF;
                case CYBERNETIC -> 0xFF23B6;
                case SINGULARITY -> 0x8820C8;
                case CYBERPSYCHO -> 0xFF3898;
            };
        }
        return 0xB04CFF; // Default purple cosmic spacetime energy
    }

    /**
     * Called when Punchy determines that an attack is a critical hit (via its consumeCritical check).
     * Fires the overhead vertical cleave beam and resets combo stage to 0.
     * Returns true if the beam was successfully sent, or false if throttled.
     */
    public static boolean onPunchyCritAttack(Minecraft mc, ItemStack held) {
        if (mc.player == null) return false;
        cancelPendingSpin(); // Any new swing cancels pending twirl and stops active spin sound
        int tier = InfiniumArmorTier.getTier(mc.player);
        if (tier < 1 || !InfiniumSwordItem.isEnergyMode(held) || !dev.justin.infinium.CrystalCharge.saberUnlocked(mc.player)) return false;

        long currentTick = mc.player.level().getGameTime();
        int baseInterval = getBaseIntervalTicks(mc.player);
        if (CLIENT_LAST_SLASH_TICK != -1L && (currentTick - CLIENT_LAST_SLASH_TICK) < baseInterval) return false;

        CLIENT_COMBO_STAGE = 0;
        CLIENT_LAST_SWING_TIME = System.currentTimeMillis();
        CLIENT_LAST_SLASH_TICK = currentTick;
        CLIENT_PENDING_CRIT_TWIRL = true;
        CLIENT_CRIT_START_TIME = System.currentTimeMillis();

        int color = resolveSwordColor(held);
        emitSlash(mc.player.getYRot(), mc.player.getXRot(), 90.0F, color, false);
        playLaunchSound(mc.player, false, tier);
        playChargeSound(mc.player, false, tier);
        return true;
    }

    /**
     * Called inside Punchy's AttackStateMachine.resolveCustomAttackClip when wielding the Infinium Sword.
     * Decides the combo step, resolves clip BEFORE sending beam, dispatches matching arc/beam,
     * and returns PunchyAttackResult with the clip and Punchy's next combo index.
     */
    public static PunchyAttackResult onPunchyResolveAttackClip(
        Minecraft mc,
        ItemStack held,
        InteractionHand hand,
        PunchyTuningConfig.CustomAnimationEntry entry
    ) {
        if (mc.player == null) return null;
        cancelPendingSpin(); // Any new swing cancels pending twirl and stops active spin sound

        // When the queued attack targeted a block, don't advance custom combo or play custom clip
        if (QUEUED_ATTACK_TARGET_BLOCK) {
            return null;
        }

        int tier = InfiniumArmorTier.getTier(mc.player);
        if (tier < 1 || !InfiniumSwordItem.isEnergyMode(held) || !dev.justin.infinium.CrystalCharge.saberUnlocked(mc.player)) {
            return null;
        }

        long now = System.currentTimeMillis();
        long currentTick = mc.player.level().getGameTime();
        int baseInterval = getBaseIntervalTicks(mc.player);
        int stabInterval = getStabIntervalTicks(mc.player);

        // Crouching attack: flat horizontal sweep
        if (mc.player.isCrouching()) {
            if (CLIENT_LAST_SLASH_TICK != -1L && (currentTick - CLIENT_LAST_SLASH_TICK) < baseInterval) {
                return null;
            }
            AnimationClip clip = PunchyBridge.resolveNamedClip("attack_1", mc, hand, entry, held);
            if (clip == null) return null;

            CLIENT_COMBO_STAGE = 0;
            CLIENT_LAST_SWING_TIME = now;
            CLIENT_LAST_SLASH_TICK = currentTick;
            int color = resolveSwordColor(held);
            emitSlash(mc.player.getYRot(), mc.player.getXRot(), 0.0F, color, false);
            playLaunchSound(mc.player, false, tier);
            playChargeSound(mc.player, false, tier);
            return new PunchyAttackResult(clip, 0);
        }

        // Inactivity timeout (>1.5 seconds) resets pattern to Left Cut
        if (now - CLIENT_LAST_SWING_TIME > 1500L) {
            CLIENT_COMBO_STAGE = 0;
        }

        int stage = CLIENT_COMBO_STAGE;
        int color = resolveSwordColor(held);

        if (stage == 0) {
            // Stage 0: Left Cut (-35° diagonal slice)
            if (CLIENT_LAST_SLASH_TICK != -1L && (currentTick - CLIENT_LAST_SLASH_TICK) < baseInterval) {
                return null;
            }
            AnimationClip clip = PunchyBridge.resolveNamedClip("attack_1", mc, hand, entry, held);
            if (clip == null) return null;

            CLIENT_COMBO_STAGE = 1;
            CLIENT_LAST_SWING_TIME = now;
            CLIENT_LAST_SLASH_TICK = currentTick;
            emitSlash(mc.player.getYRot(), mc.player.getXRot(), -35.0F, color, false);
            playLaunchSound(mc.player, false, tier);
            playChargeSound(mc.player, false, tier);
            return new PunchyAttackResult(clip, 1);
        } else if (stage == 1) {
            // Stage 1: Right Cut (+35° diagonal back-slash)
            if (CLIENT_LAST_SLASH_TICK != -1L && (currentTick - CLIENT_LAST_SLASH_TICK) < baseInterval) {
                return null;
            }
            AnimationClip clip = PunchyBridge.resolveNamedClip("attack_2", mc, hand, entry, held);
            if (clip == null) return null;

            CLIENT_COMBO_STAGE = 2;
            CLIENT_LAST_SWING_TIME = now;
            CLIENT_LAST_SLASH_TICK = currentTick;
            emitSlash(mc.player.getYRot(), mc.player.getXRot(), 35.0F, color, false);
            playLaunchSound(mc.player, false, tier);
            // Next attack will be the heavy stab, play 25% longer deeper vacuum surge
            playChargeSound(mc.player, true, tier);
            return new PunchyAttackResult(clip, 2);
        } else {
            // Stage 2: Heavy Piercing Stab Beam (requires 25% longer recovery gate)
            if (CLIENT_LAST_SLASH_TICK != -1L && (currentTick - CLIENT_LAST_SLASH_TICK) < stabInterval) {
                // Swung too early for the heavy stab -> reset combo pattern to Left Cut
                CLIENT_COMBO_STAGE = 0;
                // If baseInterval has elapsed, fire Stage 0 Left Cut and play attack_1 (matching clip to sent beam)
                if (CLIENT_LAST_SLASH_TICK == -1L || (currentTick - CLIENT_LAST_SLASH_TICK) >= baseInterval) {
                    AnimationClip clip = PunchyBridge.resolveNamedClip("attack_1", mc, hand, entry, held);
                    if (clip == null) return null;

                    CLIENT_COMBO_STAGE = 1;
                    CLIENT_LAST_SWING_TIME = now;
                    CLIENT_LAST_SLASH_TICK = currentTick;
                    emitSlash(mc.player.getYRot(), mc.player.getXRot(), -35.0F, color, false);
                    playLaunchSound(mc.player, false, tier);
                    playChargeSound(mc.player, false, tier);
                    return new PunchyAttackResult(clip, 1);
                }
                // If even baseInterval has not elapsed, send NO beam and return null (never attack_1 with no beam)
                return null;
            }

            // Valid heavy stab: resolve clip before sending beam
            AnimationClip clip = PunchyBridge.resolveNamedClip("attack_3", mc, hand, entry, held);
            if (clip == null) return null;

            CLIENT_COMBO_STAGE = 0; // Pattern resets after stab
            CLIENT_PENDING_SPIN = true;
            CLIENT_STAB_FINISH_TIME = now;
            CLIENT_LAST_SWING_TIME = now;
            CLIENT_LAST_SLASH_TICK = currentTick;
            emitSlash(mc.player.getYRot(), mc.player.getXRot(), 0.0F, color, true);
            playLaunchSound(mc.player, true, tier);
            playChargeSound(mc.player, false, tier);
            return new PunchyAttackResult(clip, 0);
        }
    }

    /**
     * Vanilla fallback: used ONLY when Punchy is NOT loaded.
     * Fires on click directly at 100% cooldown scale matching server rate limits.
     */
    public static void onStartAttackVanilla(LocalPlayer player, ItemStack held) {
        int tier = InfiniumArmorTier.getTier(player);
        if (tier < 1 || !InfiniumSwordItem.isEnergyMode(held) || !dev.justin.infinium.CrystalCharge.saberUnlocked(player)) return;
        cancelPendingSpin();

        // All combo timing is in client game ticks (same clock as the attack-strength ticker and the server
        // rate limit). Read the charge here at startAttack HEAD, i.e. BEFORE vanilla resets the ticker for this click.
        long tick = player.level().getGameTime();
        boolean charged = player.getAttackStrengthScale(0.0F) >= 0.99F;

        // Post-stab twirl cooldown: clicks during the twirl do nothing to the combo (vanilla swing still happens).
        if (tick < CLIENT_LOCK_UNTIL_TICK) return;

        if (!charged) {           // early click = regular swing, pattern resets to left, right, stab
            CLIENT_COMBO_STAGE = 0;
            return;
        }
        if (CLIENT_COMBO_STAGE != 0 && tick - CLIENT_LAST_SLASH_TICK > dev.justin.infinium.SwordCombo.stabWindowTicks(player)) {
            CLIENT_COMBO_STAGE = 0;  // waited too long
        }
        // Safety: never emit faster than the server will accept (charged already implies this).
        if (CLIENT_LAST_SLASH_TICK != -1L && tick - CLIENT_LAST_SLASH_TICK < dev.justin.infinium.SwordCombo.minIntervalTicks(player)) return;

        int color = resolveSwordColor(held);
        CLIENT_LAST_SWING_TIME = System.currentTimeMillis();

        // A due stab ALWAYS wins (even mid-jump or crouched): that was part of the ~50% miss rate.
        if (CLIENT_COMBO_STAGE == 2) {
            CLIENT_COMBO_STAGE = 0;
            CLIENT_LAST_SLASH_TICK = tick;
            CLIENT_LOCK_UNTIL_TICK = tick + dev.justin.infinium.SwordCombo.postStabLockTicks(player);
            emitSlash(player.getYRot(), player.getXRot(), 0.0F, color, true);
            playLaunchSound(player, ModSounds.STAB_LAUNCH, tier);
            playChargeSound(player, true, tier);   // long charge-up under the twirl cooldown
            return;
        }

        boolean isJumpCrit = !player.onGround() && player.fallDistance > 0.0F && !player.isInWater() && !player.onClimbable();
        if (isJumpCrit || player.isCrouching()) {   // overhead cleave / flat sweep: standalone, resets the pattern
            CLIENT_COMBO_STAGE = 0;
            CLIENT_LAST_SLASH_TICK = tick;
            emitSlash(player.getYRot(), player.getXRot(), isJumpCrit ? 90.0F : 0.0F, color, false);
            playLaunchSound(player, ModSounds.SLASH_LAUNCH, tier);
            playChargeSound(player, false, tier);
            return;
        }

        CLIENT_LAST_SLASH_TICK = tick;
        if (CLIENT_COMBO_STAGE == 0) {
            CLIENT_COMBO_STAGE = 1;
            emitSlash(player.getYRot(), player.getXRot(), -35.0F, color, false);
            playLaunchSound(player, ModSounds.SLASH_LAUNCH, tier);
        } else {
            CLIENT_COMBO_STAGE = 2;
            emitSlash(player.getYRot(), player.getXRot(), 35.0F, color, false);
            playLaunchSound(player, ModSounds.SLASH2_LAUNCH, tier);
        }
        playChargeSound(player, false, tier);
    }

    /** Client: post-stab twirl cooldown still running (HUD meter shows it). */
    public static float lockProgress(Player player) {
        long tick = player.level().getGameTime();
        if (tick >= CLIENT_LOCK_UNTIL_TICK) return 1F;
        float total = dev.justin.infinium.SwordCombo.postStabLockTicks(player);
        return Math.max(0F, 1F - (CLIENT_LOCK_UNTIL_TICK - tick) / total);
    }

    /**
     * Single choke point for every energy slash / beam this client emits. The animation move is
     * derived from the exact payload values (roll + isStab), so the swing always matches the beam.
     */
    private static void emitSlash(float yaw, float pitch, float roll, int color, boolean isStab) {
        if (!ClientPlayNetworking.canSend(SwordSlashPayload.TYPE)) return;
        ClientPlayNetworking.send(new SwordSlashPayload(yaw, pitch, roll, color, isStab));
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            dev.justin.infinium.client.anim.SwordAnimState.recordEmittedSlash(mc.player, roll, isStab);
        }
    }

    private static void playLaunchSound(Player player, boolean isStab, int tier) {
        playLaunchSound(player, isStab ? ModSounds.STAB_LAUNCH : ModSounds.SLASH_LAUNCH, tier);
    }

    /** Local launch sound for combo step kind (SLASH_LAUNCH = 1, SLASH2_LAUNCH = 2, STAB_LAUNCH = 3). */
    private static void playLaunchSound(Player player, int kind, int tier) {
        float vol = tier == 1 ? 0.75F : (tier == 2 ? 0.9F : 1.0F);
        Holder<SoundEvent> s = ModSounds.sword(player, kind, kind == ModSounds.STAB_LAUNCH ? ModSounds.SWORD_STAB_LAUNCH : ModSounds.SWORD_SLASH_LAUNCH);
        if (s != null) {
            player.level().playSound(player, player.getX(), player.getY(), player.getZ(), s.value(), SoundSource.PLAYERS, vol, 1.0F);
        }
    }

    private static void playChargeSound(Player player, boolean isStabCharge, int tier) {
        double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        float pitch = (float) Math.min(1.35, Math.max(0.9, attackSpeed / dev.justin.infinium.SwordCombo.BASE_ATTACK_SPEED));   // faster cooldown = shorter, higher charge
        float baseVol = isStabCharge ? 0.40F : 0.32F;   // subtle bed under the launch sounds
        float vol = baseVol * (tier == 1 ? 0.70F : (tier == 2 ? 0.85F : 1.0F));
        if (isStabCharge) {
            Holder<SoundEvent> s = ModSounds.sword(player, ModSounds.STAB_CHARGE, ModSounds.SWORD_STAB_CHARGE);
            if (s != null) {
                player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
                    s.value(), SoundSource.PLAYERS, vol, pitch);
            }
        } else {
            Holder<SoundEvent> s = ModSounds.sword(player, ModSounds.SLASH_CHARGE, ModSounds.SWORD_SLASH_CHARGE);
            if (s != null) {
                player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
                    s.value(), SoundSource.PLAYERS, vol, pitch);
            }
        }
    }

    /** Client tick. The twirl / crit-twirl sounds were removed by request (2026-10-29); only cleanup remains. */
    public static void clientTick(Minecraft mc) {
        CLIENT_PENDING_SPIN = false;
        CLIENT_PENDING_CRIT_TWIRL = false;
        if (ACTIVE_SPIN_SOUND != null) stopActiveSpinSound();
    }
}
