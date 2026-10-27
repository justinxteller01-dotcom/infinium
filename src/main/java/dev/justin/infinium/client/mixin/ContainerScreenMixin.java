package dev.justin.infinium.client.mixin;

import dev.justin.infinium.SetLook;
import dev.justin.infinium.client.InfiniumClientConfig;
import dev.justin.infinium.client.InventoryHudTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders progressive Infinium ambient effects on the player's inventory UI panel (#20, #30).
 * Per-slot crystal buttons and chestplate elytra icon buttons are removed (#6, #26).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Shadow @Final protected AbstractContainerMenu menu;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;

    @Inject(method = "extractLabels", at = @At("HEAD"))
    private void infinium$inventoryFx(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof InventoryScreen) && !(self instanceof CreativeModeInventoryScreen)) return;
        if (!InfiniumClientConfig.uiEffects()) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        InventoryHudTheme.draw(g, 0, 0, this.imageWidth, this.imageHeight, SetLook.of(player));
    }
}
