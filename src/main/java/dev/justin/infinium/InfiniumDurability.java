package dev.justin.infinium;

import net.minecraft.world.item.ItemStack;

/** R8 13.5: Infinium gear never breaks. At <= 50 remaining it is "disabled" (no attributes, perks, gliding, mining) until repaired. */
public final class InfiniumDurability {
    public static final int FLOOR = PerkValues.DURABILITY_FLOOR;
    public static boolean isGear(ItemStack s) { return s != null && !s.isEmpty() && (Infinium.isArmor(s) || Infinium.isTool(s)) && s.isDamageableItem(); }
    public static int floor(ItemStack s) { return Math.max(0, s.getMaxDamage() - PerkValues.durabilityFloor()); }
    public static boolean disabled(ItemStack s) { return isGear(s) && s.getDamageValue() >= floor(s); }
    private InfiniumDurability() {}
}
