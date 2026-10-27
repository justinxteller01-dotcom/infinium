package dev.justin.infinium.compat.mixin.tb;
import dev.justin.infinium.compat.tb.*;

import com.tiviacz.travelersbackpack.inventory.handler.ItemStackHandler;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Full-set stack buff inside a carried (worn / Accessories / held) backpack's storage. Placed backpacks stay vanilla. */
@Mixin(value = ItemStackHandler.class, remap = false)
public abstract class ItemStackHandlerMixin {
    @Inject(method = "getStackLimit", at = @At("RETURN"), cancellable = true, require = 1)
    private void infinium$buff(int slot, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        int l = TbStacks.limit(this, stack);
        if (l > cir.getReturnValueI()) cir.setReturnValue(l);
    }
}
