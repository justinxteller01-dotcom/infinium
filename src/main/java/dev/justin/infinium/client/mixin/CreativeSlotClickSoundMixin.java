package dev.justin.infinium.client.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Creative inventory overrides slotClicked and does not always call super. require 0: skip if 26.2 renamed it. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeSlotClickSoundMixin {

    @Inject(method = "slotClicked", at = @At("HEAD"), require = 0)
    private void infinium$creativeClick(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        dev.justin.infinium.client.InfiniumUiSounds.slotClick(((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) (Object) this).getMenu(), slot);
    }
}
