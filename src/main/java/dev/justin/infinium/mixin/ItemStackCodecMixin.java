package dev.justin.infinium.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Persistence: vanilla ItemStack.CODEC limits "count" to 1..99, so a 128 stack could not be saved (player data).
 *  Widen to 1..1024 (the save/load range only; real limits stay per item/slot). */
@Mixin(ItemStack.class)
public abstract class ItemStackCodecMixin {
    @ModifyConstant(method = "lambda$static$1", constant = @Constant(intValue = 99), require = 1)
    private static int infinium$countRange(int old) { return 1024; }
}
