package dev.justin.infinium.mixin;

import dev.justin.infinium.SingularityStacks;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Inventory.add / pickup merging (addResource, hasRemainingSpaceForItem) use this per-stack limit. */
@Mixin(Inventory.class)
public abstract class InventoryStackMixin {
    @Shadow @Final public Player player;
    public int getMaxStackSize(ItemStack stack) { return SingularityStacks.limit(player, stack); }
}
