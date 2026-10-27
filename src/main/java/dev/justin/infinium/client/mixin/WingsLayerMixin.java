package dev.justin.infinium.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.client.InfiniumWingsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts WingsLayer for Infinium & Infinity chestplates:
 *  - Completely bypasses vanilla's flat box ElytraModel and equipment layer.
 *  - Delegates directly to InfiniumWingsRenderer (custom 3D multi-piece models, dynamic crystal opacity,
 *    unlocked perk milestone pieces, zero-clipping resting stance, walking clearance, and emissive glow).
 */
@Mixin(WingsLayer.class)
public abstract class WingsLayerMixin {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void infinium$wings(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (state.chestEquipment != null && Infinium.isChestplate(state.chestEquipment)) {
            InfiniumWingsRenderer.render(pose, collector, light, state, yRot, xRot);
            ci.cancel();
        }
    }
}
