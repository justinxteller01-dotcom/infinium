package dev.justin.infinium.mixin;

import dev.justin.infinium.CrystalCharge;
import dev.justin.infinium.EnchantRules;
import dev.justin.infinium.SmithingEnchantEntry;
import dev.justin.infinium.GlowColor;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumStyle;
import dev.justin.infinium.StyleTemplates;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Style template rules + result fix-ups (the vanilla transform copies the base's whole component patch). */
@Mixin(SmithingTransformRecipe.class)
public abstract class SmithingTransformRecipeMixin {

    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/SmithingRecipeInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"), cancellable = true)
    private void infinium$fix(SmithingRecipeInput input, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack out = cir.getReturnValue();
        if (out.isEmpty()) return;
        if (Infinium.isInfinity(out) && !Infinium.isInfinity(input.base())) {   // creation; recolour of an Infinity piece falls through to the dye fix-up
            infinium$infinity(out, input.template(), input.base());
            CrystalCharge.onTierUp(out, input, cir);
            return;
        }
        if (out.is(Infinium.NETHERITE_ROCKET)) {   // Infinium Rocket from any firework: flight 1, no stars, no copied name
            out.set(DataComponents.FIREWORKS, new net.minecraft.world.item.component.Fireworks(1, List.of()));
            out.remove(DataComponents.CUSTOM_NAME);
            return;
        }
        GlowColor.applyFromTemplate(out, input.template());   // re-dyeing: new dye beats the copied old colour
        InfiniumStyle ns = StyleTemplates.styleOf(input.template().getItem());     // restyle: template wins over the copied style
        if (ns != null) out.set(InfiniumStyle.STYLE, ns);
        CrystalCharge.onSmith(out, input, cir);
    }

    /** R7/R8 12.2 + 13.1 + 13.3: Singularity + Cyber Psycho piece (either slot) -> Infinity. Name, dyes, wing mode reset, trim kept,
     *  full durability; enchants combined like two books (vanilla cap), conflicts resolved: higher level wins, tie -> base slot. */
    private static void infinium$infinity(ItemStack out, ItemStack tpl, ItemStack base) {
        out.remove(InfiniumStyle.STYLE);
        out.remove(DataComponents.CUSTOM_NAME);
        out.remove(GlowColor.GLOW_COLOR);
        out.remove(DataComponents.DYED_COLOR);
        out.remove(DataComponents.REPAIR_COST);
        out.setDamageValue(0);
        if (Infinium.isChestplate(out)) out.set(DataComponents.GLIDER, Unit.INSTANCE); else out.remove(DataComponents.GLIDER);
        ItemEnchantments eb = EnchantmentHelper.getEnchantmentsForCrafting(base), et = EnchantmentHelper.getEnchantmentsForCrafting(tpl);
        LinkedHashSet<Holder<Enchantment>> order = new LinkedHashSet<>(eb.keySet()); order.addAll(et.keySet());   // base first
        List<SmithingEnchantEntry> cs = new ArrayList<>(); int i = 0;
        for (Holder<Enchantment> h : order) {
            int lb = eb.getLevel(h), lt = et.getLevel(h), max = h.value().getMaxLevel();
            int lv = (lb > 0 && lb == lt) ? Math.max(lb, Math.min(lb + 1, max)) : Math.max(lb, lt);
            cs.add(new SmithingEnchantEntry(h, lv, lb > 0 && lb >= lt, i++));
        }
        cs.sort(Comparator.comparingInt((SmithingEnchantEntry c) -> -c.lv()).thenComparing(c -> !c.fromBase()).thenComparingInt(SmithingEnchantEntry::idx));
        ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (SmithingEnchantEntry c : cs) {
            boolean ok = true;
            for (Holder<Enchantment> k : m.keySet()) if (!EnchantRules.compatible(k, c.h())) { ok = false; break; }
            if (ok) m.set(c.h(), c.lv());
        }
        EnchantmentHelper.setEnchantments(out, m.toImmutable());
    }
}
