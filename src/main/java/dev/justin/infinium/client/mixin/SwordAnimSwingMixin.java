package dev.justin.infinium.client.mixin;

import dev.justin.infinium.client.anim.SwordAnimState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records our own swing clock (client side). priority 500 so it runs before other mods' swing hooks/cancels. */
@Mixin(value = LivingEntity.class, priority = 500)
public abstract class SwordAnimSwingMixin {
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
    private void infinium$recordSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) SwordAnimState.onSwing(self, hand);
    }
}
