package dev.justin.infinium.mixin;

import dev.justin.infinium.SingularityStacks;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Singularity stacks of 2 identical unstackables (tools, armor, buckets...): any component change (durability damage,
 *  repair, enchanting, mode toggles, renaming) splits the stack first so only one copy changes. Damage goes through
 *  setDamageValue -> set(DAMAGE), so hurtAndBreak only ever damages/breaks the single split-off item. */
@Mixin(ItemStack.class)
public abstract class ItemStackSplitMixin {
    private ItemStack infinium$self() { return (ItemStack) (Object) this; }

    @Inject(method = "set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;", at = @At("HEAD"))
    private void infinium$setHead(CallbackInfoReturnable<Object> cir) { SingularityStacks.beforeChange(infinium$self()); }
    @Inject(method = "set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;", at = @At("RETURN"))
    private void infinium$setRet(CallbackInfoReturnable<Object> cir) { SingularityStacks.afterChange(); }

    @Inject(method = "set(Lnet/minecraft/core/component/TypedDataComponent;)Ljava/lang/Object;", at = @At("HEAD"))
    private void infinium$tsetHead(CallbackInfoReturnable<Object> cir) { SingularityStacks.beforeChange(infinium$self()); }
    @Inject(method = "set(Lnet/minecraft/core/component/TypedDataComponent;)Ljava/lang/Object;", at = @At("RETURN"))
    private void infinium$tsetRet(CallbackInfoReturnable<Object> cir) { SingularityStacks.afterChange(); }

    @Inject(method = "remove", at = @At("HEAD"))
    private void infinium$remHead(CallbackInfoReturnable<Object> cir) { SingularityStacks.beforeChange(infinium$self()); }
    @Inject(method = "remove", at = @At("RETURN"))
    private void infinium$remRet(CallbackInfoReturnable<Object> cir) { SingularityStacks.afterChange(); }

    @Inject(method = "applyComponents(Lnet/minecraft/core/component/DataComponentPatch;)V", at = @At("HEAD"))
    private void infinium$patchHead(CallbackInfo ci) { SingularityStacks.beforeChange(infinium$self()); }
    @Inject(method = "applyComponents(Lnet/minecraft/core/component/DataComponentPatch;)V", at = @At("RETURN"))
    private void infinium$patchRet(CallbackInfo ci) { SingularityStacks.afterChange(); }

    @Inject(method = "applyComponents(Lnet/minecraft/core/component/DataComponentMap;)V", at = @At("HEAD"))
    private void infinium$mapHead(CallbackInfo ci) { SingularityStacks.beforeChange(infinium$self()); }
    @Inject(method = "applyComponents(Lnet/minecraft/core/component/DataComponentMap;)V", at = @At("RETURN"))
    private void infinium$mapRet(CallbackInfo ci) { SingularityStacks.afterChange(); }
}
