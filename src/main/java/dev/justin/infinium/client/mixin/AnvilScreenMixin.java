package dev.justin.infinium.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 12.11 / R7: renaming is free (cost 0 with a result) -> vanilla shows nothing, so label it "Rename: Free" in the cost spot. */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin extends ItemCombinerScreen<AnvilMenu> {
    private AnvilScreenMixin(AnvilMenu m, Inventory i, Component t, net.minecraft.resources.Identifier bg) { super(m, i, t, bg); }

    @Inject(method = "extractLabels", at = @At("TAIL"))
    private void infinium$freeRename(GuiGraphicsExtractor g, int mx, int my, CallbackInfo ci) {
        AnvilMenu m = this.menu;
        if (m.getCost() != 0 || !m.getSlot(2).hasItem()) return;
        Component c = Component.translatable("container.infinium.rename_free");
        int x = this.imageWidth - 8 - this.font.width(c) - 2;
        g.fill(x - 2, 67, this.imageWidth - 8, 79, 0x4F000000);
        g.text(this.font, c, x, 69, 0xFF80FF20, true);
    }
}
