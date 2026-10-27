package dev.justin.infinium.client.mixin;

import dev.justin.infinium.client.InfiniumClientConfig;
import dev.justin.infinium.client.UiInfection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** pass2: out-of-world menu backdrop. Only when UI Effects is on AND no world is loaded (Minecraft.level == null):
 *  replaces panorama + blur + menu background with UiInfection.menuBackdrop. Off, or in a world: vanilla, untouched.
 *  TitleScreen draws its panorama via extractPanorama from extractRenderState, so that is cancelled too. require 0. */
@Mixin({Screen.class, TitleScreen.class})
public class TitleScreenFxMixin {
    private static boolean infinium$active() {
        return InfiniumClientConfig.uiEffects() && Minecraft.getInstance().level == null;
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void infinium$bg(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt, CallbackInfo ci) {
        if (!infinium$active()) return;
        var win = Minecraft.getInstance().getWindow();
        UiInfection.menuBackdrop(g, win.getGuiScaledWidth(), win.getGuiScaledHeight());
        ci.cancel();
    }

    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true, require = 0)
    private void infinium$pano(GuiGraphicsExtractor g, float pt, CallbackInfo ci) {
        if (!infinium$active()) return;
        if ((Object) this instanceof TitleScreen) {
            var win = Minecraft.getInstance().getWindow();
            UiInfection.menuBackdrop(g, win.getGuiScaledWidth(), win.getGuiScaledHeight());
        }
        ci.cancel();
    }
}
