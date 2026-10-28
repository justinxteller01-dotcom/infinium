package dev.justin.infinium;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Sword slash/beam DoT: 1 magic damage every 20 ticks. Two registrations share this code:
 * "bleeding" (Astral/Singularity/Infinity owners, unchanged) and "burning" (Cyberburn, Cybernetic/Cyber Psycho owners):
 * same damage/timing, plus pink/purple ember dust every 4 ticks. Cyberburn never sets the mob on fire.
 */
public class BleedingEffect extends MobEffect {
    private static final DustParticleOptions[] EMBERS = {
        new DustParticleOptions(0xFF4FD8, 0.7F), new DustParticleOptions(0xB04CFF, 0.6F), new DustParticleOptions(0x2A0A33, 0.6F)};
    private final boolean burn;
    public BleedingEffect() { this(false); }
    public BleedingEffect(boolean burn) { super(MobEffectCategory.HARMFUL, burn ? 0xFF4FD8 : 0x5A0A3F); this.burn = burn; }
    @Override public boolean shouldApplyEffectTickThisTick(int tickCount, int amp) { return burn ? tickCount % 4 == 0 : tickCount % 20 == 0; }
    @Override public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amp) {
        if (burn) {
            for (int i = 0; i < 3; i++) {
                double x = mob.getRandomX(0.6), y = mob.getRandomY(), z = mob.getRandomZ(0.6);
                level.sendParticles(EMBERS[mob.getRandom().nextInt(EMBERS.length)], x, y, z, 1, 0.0, 0.05, 0.0, 0.01);
            }
            MobEffectInstance e = mob.getEffect(Infinium.BURNING);
            if (e == null || e.getDuration() % 20 != 0) return true;   // damage on the same 20-tick beat as Bleed
        }
        DotDamage.hurt(level, mob, mob.damageSources().magic(), 1.0F);   // no i-frames from DoT
        return true;
    }
    /** Slash/beam DoT length by the owner's full set: 60 ticks Astral/Cybernetic, 80 Singularity/Cyber Psycho, 100 Infinity. */
    public static int durationTicks(net.minecraft.world.entity.Entity owner) {
        SetLook l = owner instanceof LivingEntity le ? SetLook.of(le) : null;
        if (l == null) return 60;
        return switch (l) { case INFINITY -> 100; case SINGULARITY, CYBERPSYCHO -> 80; default -> 60; };
    }
    /** Bleed, or Cyberburn when the owner wears a full Cybernetic / Cyber Psycho set. */
    public static net.minecraft.core.Holder<MobEffect> forOwner(net.minecraft.world.entity.Entity owner) {
        SetLook l = owner instanceof LivingEntity le ? SetLook.of(le) : null;
        return (l == SetLook.CYBERNETIC || l == SetLook.CYBERPSYCHO) ? Infinium.BURNING : Infinium.BLEEDING;
    }
}
