package dev.justin.infinium.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes LivingEntity.lastHurt so DoT ticks can clear it (see DotDamage). */
@Mixin(LivingEntity.class)
public interface LivingEntityHurtAccessor {
    @Accessor("lastHurt") void infinium$setLastHurt(float v);
}
