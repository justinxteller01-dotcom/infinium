package dev.justin.infinium.client.mixin;

import dev.justin.infinium.client.anim.InfiniumSwordAnimator;
import dev.justin.infinium.client.anim.SwordPoseState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third-person sword pose. Runs at TAIL of setupAnim, after vanilla's arm/attack pose,
 * and overwrites only the sword arm, only when the render state says an Infinium sword is held.
 */
@Mixin(value = HumanoidModel.class, priority = 1500)
public abstract class SwordAnimHumanoidModelMixin {
    @Shadow @Final public net.minecraft.client.model.geom.ModelPart rightArm;
    @Shadow @Final public net.minecraft.client.model.geom.ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void infinium$swordPose(HumanoidRenderState state, CallbackInfo ci) {
        if (!(state instanceof SwordPoseState s) || !s.infinium$swordActive()) return;
        if (state.isUsingItem) return;
        HumanoidArm arm = state.mainArm == null ? HumanoidArm.RIGHT : state.mainArm;
        InfiniumSwordAnimator.applyThirdPerson(arm == HumanoidArm.RIGHT ? rightArm : leftArm, arm,
                state.xRot * ((float) Math.PI / 180F), s.infinium$swordSwing(), s.infinium$swordSide(),
                s.infinium$swordEquip(), s.infinium$swordTime());
    }
}
