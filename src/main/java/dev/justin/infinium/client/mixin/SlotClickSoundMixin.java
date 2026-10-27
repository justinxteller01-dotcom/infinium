package dev.justin.infinium.client.mixin;

import dev.justin.infinium.Infinium;
import dev.justin.infinium.ModSounds;
import dev.justin.infinium.Perk;
import dev.justin.infinium.client.InfiniumUiSounds;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pickup / place. Injected at HEAD of slotClicked, which is what survival, creative (via super) and Spatial GUI
 * screens call for a cursor click, a swap and a shift-click. A second click stops the sound that is already playing.
 * Diagnosis: the old mixin did play, but only through SoundManager.play with no stop, and a creative override that
 * does not call super never reached it. CreativeSlotClickSoundMixin covers that override (require 0).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class SlotClickSoundMixin {
    @Shadow @Final protected AbstractContainerMenu menu;

    @Inject(method = "slotClicked", at = @At("HEAD"))
    private void infinium$clickSound(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        InfiniumUiSounds.slotClick(menu, slot);
    }
}
