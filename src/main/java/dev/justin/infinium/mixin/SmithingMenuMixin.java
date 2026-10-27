package dev.justin.infinium.mixin;

import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumStyle;
import dev.justin.infinium.StyleTemplates;
import java.util.function.Predicate;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Style-template rules (same-tier swaps consume the template like vanilla), and the Infinity recipe gate + template-slot filter (13.2). */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin {
    @Unique private static boolean infinium$infArmor(ItemStack s) { return s.is(Infinium.HELMET) || s.is(Infinium.CHESTPLATE) || s.is(Infinium.LEGGINGS) || s.is(Infinium.BOOTS); }
    @Unique private static boolean infinium$ultra(ItemStack s) {
        InfiniumStyle st = InfiniumStyle.get(s);
        return infinium$infArmor(s) && (st == InfiniumStyle.SINGULARITY || st == InfiniumStyle.CYBERPSYCHO);
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void infinium$rules(CallbackInfo ci) {
        SmithingMenu m = (SmithingMenu) (Object) this;
        ItemStack tpl = m.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem(), base = m.getSlot(SmithingMenu.BASE_SLOT).getItem(), add = m.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem();
        ItemStack res = m.getSlot(SmithingMenu.RESULT_SLOT).getItem();
        if (Infinium.isInfinity(res) && !Infinium.isInfinity(base)) {   // backstop: Singularity + Cyber Psycho (either order), same item, + ingot
            InfiniumStyle a = InfiniumStyle.get(tpl), b = InfiniumStyle.get(base);
            boolean pair = (a == InfiniumStyle.SINGULARITY && b == InfiniumStyle.CYBERPSYCHO) || (a == InfiniumStyle.CYBERPSYCHO && b == InfiniumStyle.SINGULARITY);
            if (!(pair && tpl.is(base.getItem()) && infinium$infArmor(base) && add.is(Infinium.INGOT))) m.getSlot(SmithingMenu.RESULT_SLOT).set(ItemStack.EMPTY);
            return;
        }
        InfiniumStyle t = StyleTemplates.styleOf(tpl.getItem());
        if (t == null) return;
        boolean ok = infinium$infArmor(base) ? StyleTemplates.allowed(InfiniumStyle.get(base), t, !add.isEmpty()) : !t.rarer;
        if (!ok) m.getSlot(SmithingMenu.RESULT_SLOT).set(ItemStack.EMPTY);
    }

    /** Template slot: Infinium armor only if Singularity / Cyber Psycho (shift-click: first ultra piece -> template, second -> base). */
    @ModifyArg(method = "createInputSlotDefinitions(Lnet/minecraft/world/item/crafting/RecipeAccess;)Lnet/minecraft/world/inventory/ItemCombinerMenuSlotDefinition;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/ItemCombinerMenuSlotDefinition$Builder;withSlot(IIILjava/util/function/Predicate;)Lnet/minecraft/world/inventory/ItemCombinerMenuSlotDefinition$Builder;", ordinal = 0),
        index = 3)
    private static Predicate<ItemStack> infinium$templateFilter(Predicate<ItemStack> p) {
        return s -> p.test(s) && (!Infinium.isArmor(s) || infinium$ultra(s));
    }
}
