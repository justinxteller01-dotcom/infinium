package dev.justin.infinium.compat.mixin.spatial;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R6e: Spatial GUI never hooks the flat guide (javap spatial-gui 1.7.1+26.2: public static boolean
 *  SpatialGUIClient.shouldHookScreen(Screen), the gate used by SpatialGUIRenderer and its Screen/Mouse/Window/Input mixins). */
@Pseudo
@Mixin(targets = "org.tastytrash.spatialGUI.client.SpatialGUIClient", remap = false)
public abstract class ShouldHookScreenMixin {
    @Inject(method = "shouldHookScreen", at = @At("HEAD"), cancellable = true, require = 1)
    private static void infinium$flatGuide(Screen screen, CallbackInfoReturnable<Boolean> cir) {
        if (screen instanceof dev.justin.infinium.client.guide.FlatGuideScreen) cir.setReturnValue(false);
    }
}
