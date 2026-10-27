package dev.justin.infinium.compat.mixin.spatial;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justin.infinium.compat.SpatialMargin;
import net.minecraft.client.Minecraft;
import org.joml.Vector2d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Spatial GUI overscan: quad UV -> window pixels. Content occupies the middle [mx, mx + size] where mx = (F - 1) / 2 * size. */
@Pseudo
@Mixin(targets = "org.tastytrash.spatialGUI.util.RenderUtil", remap = false)
public abstract class RenderUtilMixin {
    @ModifyReturnValue(method = {"getInventoryMousePositionRay", "getInventoryMousePositionRayCurved"}, at = @At("RETURN"), require = 2)
    private static Vector2d infinium$unscan(Vector2d p) {
        return p;
    }
}
