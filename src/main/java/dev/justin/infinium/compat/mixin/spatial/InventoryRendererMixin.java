package dev.justin.infinium.compat.mixin.spatial;

import dev.justin.infinium.compat.SpatialMargin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Spatial GUI overscan, curved screen: arc x F with width x F keeps the same radius, so content does not move. */
@Pseudo
@Mixin(targets = "org.tastytrash.spatialGUI.render.InventoryRenderer", remap = false)
public abstract class InventoryRendererMixin {
    @ModifyArg(method = {"updateScreenBases", "addScreenQuadMesh"}, at = @At(value = "INVOKE", target = "Ljava/lang/Math;toRadians(D)D"), require = 2)
    private double infinium$arc(double deg) { return Math.min(340.0, deg * SpatialMargin.F); }
}
