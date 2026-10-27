package dev.justin.infinium.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.justin.infinium.NoConsume;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockItem.class)
public abstract class BlockItemNoConsumeMixin {
    @WrapOperation(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
    private void infinium$keep(ItemStack s, int n, LivingEntity e, Operation<Void> op) {
        // Item 1: food and fireworks do not come through here. Disallowed blocks always consume.
        if (!NoConsume.blockAllowed(s) || !NoConsume.roll(e, s)) op.call(s, n, e);
    }
}
