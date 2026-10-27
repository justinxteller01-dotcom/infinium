package dev.justin.infinium.compat.mixin.jei;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * R5b JEI: Infinium draws status effects as a row above the inventory (client EffectsInInventoryMixin). JEI 30.x still
 * reserves the old right-hand column: VanillaPlugin's InventoryEffectRendererGuiHandler.getGuiExtraAreas and
 * IngredientListOverlay.shouldRenderCompactInventoryEffects both call InventoryEffectRenderer.getEffectAreas(screen, compact),
 * which measures the vanilla side panel. Return nothing; InfiniumJeiPlugin registers the real top strip instead.
 */
@Pseudo
@Mixin(targets = "mezz.jei.common.gui.InventoryEffectRenderer", remap = false)
public abstract class InventoryEffectRendererMixin {
    @Inject(method = "getEffectAreas", at = @At("HEAD"), cancellable = true, require = 1)
    private static void infinium$noSideColumn(CallbackInfoReturnable<List<?>> cir) { cir.setReturnValue(List.of()); }
}
