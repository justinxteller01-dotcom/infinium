package dev.justin.infinium.client.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Infinium mode messages ("Mode: ...") stay above the hotbar for 5 s, then use vanilla's 1 s fade-out. */
@Mixin(Hud.class)
public abstract class HudOverlayMixin {
    @Shadow private int overlayMessageTime;
    @Inject(method = "setOverlayMessage", at = @At("TAIL"))
    private void infinium$longer(Component msg, boolean animate, CallbackInfo ci) {
        if (msg != null && msg.getString().startsWith("Mode: ")) overlayMessageTime = 120;   // 100 ticks solid + 20 tick fade
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void infinium$dungeonHud(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker dt, CallbackInfo ci) {
        dev.justin.infinium.client.DungeonHudClient.render(g);
        dev.justin.infinium.client.HelmetVisionHud.render(g);
        dev.justin.infinium.client.ability.AbilityHud.render(g);   // M1 ability bar
    }
}
