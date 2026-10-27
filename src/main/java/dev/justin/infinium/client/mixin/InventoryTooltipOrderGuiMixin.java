package dev.justin.infinium.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.justin.infinium.client.InventoryTooltipOrder;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * G4: outer wrap around the screen extract call. Priority 1500 so this wrap applies after (i.e. outside)
 * Fabric Screen API's wrap, which fires ScreenEvents.afterExtract (where owo-lib draws the Accessories
 * inventory button layer). Tooltips deferred by InventoryTooltipOrderScreenMixin are flushed here, after it.
 */
@Mixin(value = Gui.class, priority = 1500)
public abstract class InventoryTooltipOrderGuiMixin {
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void infinium$tooltipsAfterLayers(Screen screen, GuiGraphicsExtractor g, int mx, int my, float a, Operation<Void> original) {
        boolean defer = InventoryTooltipOrder.applies(screen);
        InventoryTooltipOrder.begin(defer);
        try {
            original.call(screen, g, mx, my, a);
        } finally {
            InventoryTooltipOrder.end(g);
        }
    }
}
