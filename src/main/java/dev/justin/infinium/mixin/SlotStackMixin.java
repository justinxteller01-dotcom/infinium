package dev.justin.infinium.mixin;

import dev.justin.infinium.SingularityStacks;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Menu slots backed by the player's main inventory / hotbar / offhand: clicks, cursor placement and shift-click
 *  (moveItemStackTo -> slot.getMaxStackSize(stack)) use the Singularity limit. Armor and container slots: vanilla. */
@Mixin(Slot.class)
public abstract class SlotStackMixin {
    @Shadow @Final public Container container;
    @Shadow public abstract int getContainerSlot();
    @Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private void infinium$stack(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (container instanceof Inventory inv && SingularityStacks.boostedSlot(getContainerSlot()))
            cir.setReturnValue(SingularityStacks.limit(inv.player, stack));
    }
}
