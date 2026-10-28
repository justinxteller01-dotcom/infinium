package dev.justin.infinium.client.mixin;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ensures vanilla / non-Infinium ElytraModel parts always retain valid scales and poses.
 * Infinium chestplates are rendered exclusively by InfiniumWingsRenderer.
 */
@Mixin(ElytraModel.class)
public abstract class ElytraModelMixin {
    @Shadow @Final private ModelPart leftWing;
    @Shadow @Final private ModelPart rightWing;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void infinium$fold(HumanoidRenderState state, CallbackInfo ci) {
        for (ModelPart w : new ModelPart[]{leftWing, rightWing}) {
            // batch1: scale only. Resetting x/y/z here wiped vanilla's crouch offset and would freeze the round-3
            // Cybernetic / Cyber Psycho elytra (InfiniumWingsRenderer.renderR3) in their rest position.
            w.xScale = 1F; w.yScale = 1F; w.zScale = 1F;
        }
    }
}
