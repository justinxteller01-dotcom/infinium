package dev.justin.infinium.client.mixin;

import dev.justin.infinium.client.DarkUi;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** pass2: lighten the vanilla 0xFF404040 container title/label text while the Infinium dark UI pack is active. */
@Mixin({AbstractContainerScreen.class, InventoryScreen.class, CreativeModeInventoryScreen.class, MerchantScreen.class})
public class ContainerLabelColorMixin {
    @ModifyConstant(method = "extractLabels", constant = @Constant(intValue = 0xFF404040), require = 0)
    private int infinium$label(int c) { return DarkUi.active() ? 0xFFD6CCE0 : c; }
}
