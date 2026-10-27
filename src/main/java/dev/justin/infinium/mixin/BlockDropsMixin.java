package dev.justin.infinium.mixin;

import dev.justin.infinium.InfiniumPickaxeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.justin.infinium.InfiniumAxeItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

@Mixin(Block.class)
public abstract class BlockDropsMixin {
    @ModifyVariable(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;",
        at = @At("HEAD"), argsOnly = true)
    private static ItemInstance infinium$pickMode(ItemInstance tool, BlockState state, ServerLevel level, BlockPos pos) {
        if (tool instanceof ItemStack s && s.getItem() instanceof InfiniumPickaxeItem && !dev.justin.infinium.InfiniumDurability.disabled(s)) return InfiniumPickaxeItem.lootTool(level, s);
        return tool;
    }

    @Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;",
        at = @At("RETURN"), cancellable = true)
    private static void infinium$axeLogs(BlockState state, ServerLevel level, BlockPos pos, BlockEntity be, Entity e, ItemInstance tool, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (tool instanceof ItemStack s && s.getItem() instanceof InfiniumAxeItem && InfiniumAxeItem.mode(s) != 0 && !dev.justin.infinium.InfiniumDurability.disabled(s)) {
            List<ItemStack> l = new ArrayList<>(cir.getReturnValue());
            InfiniumAxeItem.convertDrops(s, l);
            cir.setReturnValue(l);
        }
    }
}
