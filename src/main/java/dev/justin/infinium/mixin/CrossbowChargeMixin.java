package dev.justin.infinium.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justin.infinium.SetBonuses;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CrossbowItem.class)
public abstract class CrossbowChargeMixin {
    @ModifyReturnValue(method = "getChargeDuration", at = @At("RETURN"))
    private static int infinium$charge(int d, ItemStack stack, LivingEntity e) {
        double b = e == null ? 0 : SetBonuses.attack(e);
        return b > 0 ? Math.max(1, (int) Math.round(d / (1 + b))) : d;
    }
}
