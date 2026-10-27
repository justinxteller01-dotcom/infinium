package dev.justin.infinium.client.mixin;

import dev.justin.infinium.client.AnimatedName;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Animated, larger title line for Singularity / CyberPsycho item tooltips (see AnimatedName). */
@Mixin(GuiGraphicsExtractor.class)
public abstract class TooltipNameMixin {
    @Inject(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V", at = @At("HEAD"))
    private void infinium$captureStack(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        AnimatedName.pending = stack;
    }
    /** Clamp tooltip on screen with 4px margin. If taller than screen, smoothly pan vertically with mouse Y so all lines remain readable. */
    @ModifyVariable(method = "tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V",
            at = @At("HEAD"), argsOnly = true)
    private net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner infinium$clamp(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner pos) {
        if (pos == null) return null;
        return (sw, sh, x, y, w, h) -> {
            org.joml.Vector2ic v = pos.positionTooltip(sw, sh, x, y, w, h);
            int margin = 4;
            int nx;
            if (w >= sw - margin * 2) {
                float progX = Math.max(0F, Math.min(1F, (float) x / (float) Math.max(1, sw)));
                nx = (int) Math.round(margin + progX * (sw - w - margin * 2));
            } else {
                nx = Math.max(margin, Math.min(v.x(), sw - w - margin));
            }
            int ny;
            if (h >= sh - margin * 2) {
                float progress = Math.max(0F, Math.min(1F, (float) y / (float) Math.max(1, sh)));
                ny = (int) Math.round(margin + progress * (sh - h - margin * 2));
            } else {
                ny = Math.max(margin, Math.min(v.y(), sh - h - margin));
            }
            return new org.joml.Vector2i(nx, ny);
        };
    }
    @ModifyVariable(method = "tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V",
            at = @At("HEAD"), argsOnly = true)
    private List<ClientTooltipComponent> infinium$animateTitle(List<ClientTooltipComponent> lines) {
        return AnimatedName.wrap(lines);
    }
}
