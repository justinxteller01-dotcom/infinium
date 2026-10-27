package dev.justin.infinium.compat.mixin.spatial;

import dev.justin.infinium.compat.SpatialMargin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Spatial GUI overscan: offscreen texture F times the window (locals width/height = int ordinals 2/3 in 1.7.1, 1.7.1-cursorfix, 1.8). */
@Pseudo
@Mixin(targets = "org.tastytrash.spatialGUI.render.TextureTargetManager", remap = false)
public abstract class TextureTargetManagerMixin {
    @ModifyVariable(method = "prepareTarget", at = @At("STORE"), ordinal = 2, require = 1)
    private int infinium$w(int w) { return SpatialMargin.grow(w); }
    @ModifyVariable(method = "prepareTarget", at = @At("STORE"), ordinal = 3, require = 1)
    private int infinium$h(int h) { return SpatialMargin.grow(h); }
}
