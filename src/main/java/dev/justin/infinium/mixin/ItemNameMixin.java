package dev.justin.infinium.mixin;

import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Display name only. Does not touch {@code getHoverName}, so an anvil custom name still wins.
 * Infinity pieces already have their own item ids and lang keys; they are not rewritten.
 * Register in the common mixin list as "ItemNameMixin".
 *
 * Item.getName(ItemStack) exists on 26.2 and returns Component. ItemStack.is(Item) does not;
 * the only is(...) takes Predicate of Holder, so this compares getItem().
 */
@Mixin(Item.class)
public abstract class ItemNameMixin {
    @Inject(method = "getName(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/network/chat/Component;",
            at = @At("RETURN"), cancellable = true, require = 1)
    private void infinium$setName(ItemStack stack, CallbackInfoReturnable<Component> cir) {
        String piece = piece(stack);
        if (piece == null) return;
        InfiniumStyle style = InfiniumStyle.get(stack);
        if (style == null) return;
        cir.setReturnValue(Component.translatable("item.infinium." + style.id + "_" + piece));
    }

    private static String piece(ItemStack stack) {
        if (stack.getItem() == Infinium.HELMET) return "helmet";
        if (stack.getItem() == Infinium.CHESTPLATE) return "chestplate";
        if (stack.getItem() == Infinium.LEGGINGS) return "leggings";
        if (stack.getItem() == Infinium.BOOTS) return "boots";
        return null;
    }
}
