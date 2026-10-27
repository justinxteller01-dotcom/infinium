package dev.justin.infinium.mixin;

import dev.justin.infinium.InfiniumDurability;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R4: Infinium gear at the durability floor has no enchantment effects (armor and tools). */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantDisableMixin {
    @Inject(method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentVisitor;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private static void infinium$a(ItemStack s, @org.spongepowered.asm.mixin.injection.Coerce Object v, CallbackInfo ci) { if (InfiniumDurability.disabled(s)) ci.cancel(); }
    @Inject(method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentInSlotVisitor;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private static void infinium$b(ItemStack s, net.minecraft.world.entity.EquipmentSlot sl, net.minecraft.world.entity.LivingEntity e, @org.spongepowered.asm.mixin.injection.Coerce Object v, CallbackInfo ci) { if (InfiniumDurability.disabled(s)) ci.cancel(); }
    @Inject(method = "forEachModifier(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlotGroup;Ljava/util/function/BiConsumer;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private static void infinium$c(ItemStack s, net.minecraft.world.entity.EquipmentSlotGroup g, java.util.function.BiConsumer<?, ?> c, CallbackInfo ci) { if (InfiniumDurability.disabled(s)) ci.cancel(); }
    @Inject(method = "forEachModifier(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private static void infinium$d(ItemStack s, net.minecraft.world.entity.EquipmentSlot g, java.util.function.BiConsumer<?, ?> c, CallbackInfo ci) { if (InfiniumDurability.disabled(s)) ci.cancel(); }
    @Inject(method = "getItemEnchantmentLevel", at = @At("HEAD"), cancellable = true, require = 1)
    private static void infinium$lvl(Holder<Enchantment> e, ItemInstance i, CallbackInfoReturnable<Integer> cir) {
        if (i instanceof ItemStack s && InfiniumDurability.disabled(s)) cir.setReturnValue(0);
    }
}
