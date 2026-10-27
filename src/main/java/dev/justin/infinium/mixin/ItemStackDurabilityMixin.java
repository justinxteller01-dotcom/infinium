package dev.justin.infinium.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justin.infinium.InfiniumDurability;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** R8 13.5: Infinium gear never breaks: damage is clamped at max-100 ("disabled"); a disabled tool mines like an empty hand. */
@Mixin(ItemStack.class)
public abstract class ItemStackDurabilityMixin {
    @ModifyVariable(method = "setDamageValue(I)V", at = @At("HEAD"), argsOnly = true)
    private int infinium$clamp(int v) {
        ItemStack s = (ItemStack) (Object) this;
        return InfiniumDurability.isGear(s) ? Math.min(v, InfiniumDurability.floor(s)) : v;
    }
    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private void infinium$noSpam(int amount, ServerLevel level, ServerPlayer player, java.util.function.Consumer<Item> onBreak, CallbackInfo ci) {
        if (amount > 0 && InfiniumDurability.disabled((ItemStack) (Object) this)) ci.cancel();
    }
    @ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"))
    private float infinium$speed(float v) { return InfiniumDurability.disabled((ItemStack) (Object) this) ? 1.0F : v; }
    @ModifyReturnValue(method = "isCorrectToolForDrops", at = @At("RETURN"))
    private boolean infinium$drops(boolean v) { return v && !InfiniumDurability.disabled((ItemStack) (Object) this); }
}
