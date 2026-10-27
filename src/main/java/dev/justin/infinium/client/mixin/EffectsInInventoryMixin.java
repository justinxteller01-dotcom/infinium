package dev.justin.infinium.client.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Status effect indicators (#19): anchored above the top-left of the inventory HUD,
 * adding each subsequent effect to the right with an Infinium-themed border around each icon.
 */
@Mixin(EffectsInInventory.class)
public abstract class EffectsInInventoryMixin {
    @Shadow @Final private AbstractContainerScreen<?> screen;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void infinium$compact(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
        ci.cancel();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        List<MobEffectInstance> fx = new ArrayList<>();
        for (MobEffectInstance e : mc.player.getActiveEffects()) if (e.showIcon()) fx.add(e);
        if (fx.isEmpty()) return;
        var acc = (ContainerScreenPosAccessor) screen;
        final int S = 14, GAP = 4;
        int x0 = acc.infinium$leftPos() + 2;
        boolean creative = screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
        int y = Math.max(2, acc.infinium$topPos() - S - (creative ? 32 : 5));
        for (int i = 0; i < fx.size(); i++) {
            MobEffectInstance e = fx.get(i);
            int x = x0 + i * (S + GAP);
            boolean hover = mouseX >= x - 1 && mouseX < x + S + 1 && mouseY >= y - 1 && mouseY < y + S + 1;
            int border = hover ? 0xFFD864FF : (e.isAmbient() ? 0xFF9A3EB8 : 0xFF6A2A78);
            // Outer Infinium border + dark cosmic inner backing
            g.fill(x - 2, y - 2, x + S + 2, y + S + 2, border);
            g.fill(x - 1, y - 1, x + S + 1, y + S + 1, 0xFF12061A);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(e.getEffect()), x, y, S, S);
            if (hover) {
                float tr = mc.level == null ? 20F : mc.level.tickRateManager().tickrate();
                Component name = e.getEffect().value().getDisplayName().copy();
                if (e.getAmplifier() > 0) name = name.copy().append(" ").append(Component.translatable("enchantment.level." + (e.getAmplifier() + 1)));
                g.setComponentTooltipForNextFrame(mc.font, List.of(name, MobEffectUtil.formatDuration(e, 1.0F, tr)), mouseX, mouseY);
            }
        }
    }
}
