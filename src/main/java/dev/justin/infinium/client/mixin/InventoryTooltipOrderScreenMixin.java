package dev.justin.infinium.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.justin.infinium.client.InventoryTooltipOrder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** G4: on inventory screens, hold back the deferred tooltip pass so it runs after ScreenEvents.afterExtract. */
@Mixin(Screen.class)
public abstract class InventoryTooltipOrderScreenMixin {
    @WrapOperation(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extractDeferredElements(IIF)V"))
    private void infinium$deferTooltip(GuiGraphicsExtractor g, int mx, int my, float a, Operation<Void> original) {
        if (!InventoryTooltipOrder.hold(mx, my, a)) original.call(g, mx, my, a);
    }
}
