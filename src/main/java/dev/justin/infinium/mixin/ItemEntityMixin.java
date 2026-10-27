package dev.justin.infinium.mixin;

import dev.justin.infinium.Infinium;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Shadow public abstract ItemStack getItem();

    private boolean infinium$isOurs() {
        ItemStack s = this.getItem();
        if (s == null || s.isEmpty()) return false;
        Identifier key = BuiltInRegistries.ITEM.getKey(s.getItem());
        return key != null && Infinium.MODID.equals(key.getNamespace());
    }

    @Inject(method = "fireImmune", at = @At("HEAD"), cancellable = true)
    private void infinium$fireImmune(CallbackInfoReturnable<Boolean> cir) {
        if (infinium$isOurs()) cir.setReturnValue(true);
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void infinium$surviveHazards(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (infinium$isOurs() && (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_LIGHTNING))) {
            cir.setReturnValue(false);
        }
    }
}
