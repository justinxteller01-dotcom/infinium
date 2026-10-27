package dev.justin.infinium;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class BleedingEffect extends MobEffect {
    public BleedingEffect() { super(MobEffectCategory.HARMFUL, 0x5A0A0A); }
    @Override public boolean shouldApplyEffectTickThisTick(int tickCount, int amp) { return tickCount % 20 == 0; }
    @Override public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amp) {
        mob.hurtServer(level, mob.damageSources().magic(), 1.0F);
        return true;
    }
}
