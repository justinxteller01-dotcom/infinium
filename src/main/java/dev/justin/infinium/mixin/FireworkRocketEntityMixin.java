package dev.justin.infinium.mixin;

import dev.justin.infinium.NetheriteRocketItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Netherite Rocket fireworks are purely cosmetic: no explosion damage to anything. */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {
    @Inject(method = "dealExplosionDamage", at = @At("HEAD"), cancellable = true)
    private void infinium$noDamage(ServerLevel level, CallbackInfo ci) {
        if (((FireworkRocketEntity) (Object) this).getItem().getItem() instanceof NetheriteRocketItem) ci.cancel();
    }
}
