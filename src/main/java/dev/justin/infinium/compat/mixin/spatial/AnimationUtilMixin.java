package dev.justin.infinium.compat.mixin.spatial;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justin.infinium.compat.SpatialMargin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Spatial GUI overscan: the screen quad is F times bigger (only caller: InventoryRenderer.computeScreenPose). */
@Pseudo
@Mixin(targets = "org.tastytrash.spatialGUI.util.AnimationUtil", remap = false)
public abstract class AnimationUtilMixin {
    @ModifyReturnValue(method = "calculateAnimatedScale", at = @At("RETURN"), require = 1)
    private static float infinium$bigger(float s) { return s * SpatialMargin.F; }
}
