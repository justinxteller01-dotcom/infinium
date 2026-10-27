package dev.justin.infinium.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.justin.infinium.NoConsume;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FireworkRocketItem.class)
public abstract class FireworkNoConsumeMixin {
    @WrapOperation(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
    private void infinium$boost(ItemStack s, int n, LivingEntity e, Operation<Void> op) { if (!NoConsume.roll(e, s)) op.call(s, n, e); }
    @WrapOperation(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
    private void infinium$launch(ItemStack s, int n, Operation<Void> op, @Local(argsOnly = true) UseOnContext ctx) { if (!NoConsume.roll(ctx.getPlayer(), s)) op.call(s, n); }
}
