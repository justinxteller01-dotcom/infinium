package dev.justin.infinium.mixin;

import dev.justin.infinium.SingularityStacks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Emptying one filled bucket from a stack of 2 (Singularity perk): vanilla would replace the whole stack with one empty
 *  bucket. Instead keep the rest in hand and give the empty bucket to the inventory (or drop it). */
@Mixin(BucketItem.class)
public abstract class BucketStackMixin {
    @Inject(method = "getEmptySuccessItem", at = @At("HEAD"), cancellable = true)
    private static void infinium$keepRest(ItemStack stack, Player player, CallbackInfoReturnable<ItemStack> cir) {
        if (player == null || player.hasInfiniteMaterials() || stack.getCount() <= 1) return;
        stack.shrink(1);
        SingularityStacks.give(player, new ItemStack(Items.BUCKET));
        cir.setReturnValue(stack);
    }
}
