package dev.justin.infinium;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;

/** Sort entry for SmithingTransformRecipeMixin (kept outside the mixin package: mixin packages may only hold mixins). */
public record SmithingEnchantEntry(Holder<Enchantment> h, int lv, boolean fromBase, int idx) {}
