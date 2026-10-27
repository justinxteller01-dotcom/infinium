package dev.justin.infinium;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/** R8 13.1: vanilla exclusivity (exclusive_set tags) for all gear, except Infinity + Mending on bows stays allowed. */
public final class EnchantRules {
    public static void init() {}
    public static boolean compatible(Holder<Enchantment> a, Holder<Enchantment> b) {
        if (a.equals(b)) return true;
        if ((a.is(Enchantments.INFINITY) && b.is(Enchantments.MENDING)) || (a.is(Enchantments.MENDING) && b.is(Enchantments.INFINITY))) return true;
        return Enchantment.areCompatible(a, b);
    }
    private EnchantRules() {}
}
