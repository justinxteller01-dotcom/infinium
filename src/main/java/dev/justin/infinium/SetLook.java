package dev.justin.infinium;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;

/** The set a wearer currently qualifies for (visuals, tools, sounds, light, set-perk family). null = none (mixed / fewer than 4). */
public enum SetLook implements StringRepresentable {
    ASTRAL("astral"), CYBERNETIC("cybernetic"), SINGULARITY("singularity"), CYBERPSYCHO("cyberpsycho"), INFINITY("infinity");
    public final String id;
    SetLook(String id) { this.id = id; }
    @Override public String getSerializedName() { return id; }
    public static final Codec<SetLook> CODEC = StringRepresentable.fromEnum(SetLook::values);
    public static SetLook of(InfiniumStyle s) { return s == null ? null : values()[s.ordinal()]; }
    /** R2: highest tier fully satisfied. Infinity pieces count for any set; Singularity also counts as Astral, Cyber Psycho as Cybernetic. */
    public PerkValues.Family family() { return PerkValues.Family.values()[ordinal()]; }
    public static SetLook of(LivingEntity e) {
        if (e == null || !Infinium.fullSet(e)) return null;
        if (Infinium.infinitySet(e)) return INFINITY;
        for (InfiniumStyle s : new InfiniumStyle[]{InfiniumStyle.SINGULARITY, InfiniumStyle.CYBERPSYCHO, InfiniumStyle.ASTRAL, InfiniumStyle.CYBERNETIC}) if (Infinium.styledSet(e, s)) return of(s);
        return null;
    }
    public static SetLook lookWith(LivingEntity e, net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.ItemStack next) {
        if (e == null || slot == null || slot.getType() != net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) return null;
        net.minecraft.world.item.ItemStack head = (slot == net.minecraft.world.entity.EquipmentSlot.HEAD) ? next : e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        net.minecraft.world.item.ItemStack chest = (slot == net.minecraft.world.entity.EquipmentSlot.CHEST) ? next : e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        net.minecraft.world.item.ItemStack legs = (slot == net.minecraft.world.entity.EquipmentSlot.LEGS) ? next : e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
        net.minecraft.world.item.ItemStack feet = (slot == net.minecraft.world.entity.EquipmentSlot.FEET) ? next : e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);

        if (!Infinium.isHelmet(head) || !Infinium.isChestplate(chest) || !Infinium.isLeggings(legs) || !Infinium.isBoots(feet)) return null;

        net.minecraft.world.item.ItemStack[] pieces = new net.minecraft.world.item.ItemStack[]{head, chest, legs, feet};
        boolean allInf = true;
        for (net.minecraft.world.item.ItemStack p : pieces) {
            if (!Infinium.isInfinity(p)) { allInf = false; break; }
        }
        if (allInf) return INFINITY;

        for (InfiniumStyle s : new InfiniumStyle[]{InfiniumStyle.SINGULARITY, InfiniumStyle.CYBERPSYCHO, InfiniumStyle.ASTRAL, InfiniumStyle.CYBERNETIC}) {
            boolean match = true;
            for (net.minecraft.world.item.ItemStack p : pieces) {
                if (!Infinium.countsAs(p, s)) { match = false; break; }
            }
            if (match) return of(s);
        }
        return null;
    }
}
