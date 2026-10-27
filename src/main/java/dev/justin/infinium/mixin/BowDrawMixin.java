package dev.justin.infinium.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.justin.infinium.SetBonuses;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Cybernetic +20 % / Cyber Psycho & Infinity +50 % bow draw speed (same factor as the attack-speed bonus). */
@Mixin(BowItem.class)
public abstract class BowDrawMixin {
    @WrapOperation(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F"))
    private float infinium$draw(int ticks, Operation<Float> op, @Local(argsOnly = true) LivingEntity e) {
        double b = SetBonuses.attack(e);
        return op.call(b > 0 ? (int) Math.round(ticks * (1 + b)) : ticks);
    }
}
