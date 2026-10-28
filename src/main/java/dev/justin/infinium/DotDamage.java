package dev.justin.infinium;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shared damage-over-time hit. Every DoT effect (Bleed, Cyberburn, any future radiation/DoT) must deal its
 * tick damage through here: the tick never grants invincibility frames, so a slash/beam/melee hit right
 * after a DoT tick lands at full damage.
 */
public final class DotDamage {
    private DotDamage() {}
    public static boolean hurt(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
        boolean hit = target.hurtServer(level, source, amount);
        target.invulnerableTime = 0;
        ((dev.justin.infinium.mixin.LivingEntityHurtAccessor) target).infinium$setLastHurt(0.0F);
        return hit;
    }
}
