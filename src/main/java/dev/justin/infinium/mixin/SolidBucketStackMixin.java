package dev.justin.infinium.mixin;

import dev.justin.infinium.SingularityStacks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SolidBucketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Powder snow bucket: BlockItem.useOn already consumed one; if one is still left (stack of 2), keep it in hand and give
 *  the empty bucket separately instead of overwriting the remaining one. */
@Mixin(SolidBucketItem.class)
public abstract class SolidBucketStackMixin {
    @Redirect(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BucketItem;getEmptySuccessItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack infinium$keepRest(ItemStack stack, Player player) {
        if (player != null && !player.hasInfiniteMaterials() && !stack.isEmpty()) {
            SingularityStacks.give(player, new ItemStack(Items.BUCKET));
            return stack;
        }
        return BucketItem.getEmptySuccessItem(stack, player);
    }
}
