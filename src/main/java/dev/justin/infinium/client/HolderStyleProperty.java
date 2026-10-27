package dev.justin.infinium.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumStyle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Item model select property infinium:holder_style - style of the Infinium chestplate the holding entity wears.
 *  No holder, no Infinium chestplate, or a pre-template (unstyled) chestplate -> ASTRAL. Used by the tool item models. */
public final class HolderStyleProperty implements SelectItemModelProperty<dev.justin.infinium.SetLook> {
    public static final HolderStyleProperty INSTANCE = new HolderStyleProperty();
    public static final SelectItemModelProperty.Type<HolderStyleProperty, dev.justin.infinium.SetLook> TYPE =
        SelectItemModelProperty.Type.create(MapCodec.unit(INSTANCE), dev.justin.infinium.SetLook.CODEC);

    /** Returns the full-set look if worn, or the highest worn Infinium/Infinity armor piece's look if wearing partial armor. */
    public static dev.justin.infinium.SetLook wornLook(LivingEntity e) {
        if (e == null) return null;
        dev.justin.infinium.SetLook l = dev.justin.infinium.SetLook.of(e);
        if (l != null) return l;
        dev.justin.infinium.SetLook best = null;
        for (EquipmentSlot sl : Infinium.ARMOR_SLOTS) {
            if (!Infinium.wearingPiece(e, sl)) continue;
            ItemStack st = e.getItemBySlot(sl);
            dev.justin.infinium.SetLook cur;
            if (Infinium.isInfinity(st)) cur = dev.justin.infinium.SetLook.INFINITY;
            else {
                InfiniumStyle is = InfiniumStyle.get(st);
                cur = is == null ? dev.justin.infinium.SetLook.ASTRAL : dev.justin.infinium.SetLook.values()[is.ordinal()];
            }
            if (best == null || cur.ordinal() > best.ordinal()) best = cur;
        }
        return best;
    }

    public static dev.justin.infinium.SetLook of(LivingEntity e) {
        if (e == null) e = net.minecraft.client.Minecraft.getInstance().player;   // GUI / no holder: the local player's worn armor
        if (e == null) return dev.justin.infinium.SetLook.ASTRAL;
        dev.justin.infinium.SetLook l = wornLook(e);
        return l == null ? dev.justin.infinium.SetLook.ASTRAL : l;
    }
    @Override public dev.justin.infinium.SetLook get(ItemStack stack, ClientLevel level, LivingEntity owner, int seed, ItemDisplayContext ctx) { return of(owner); }
    @Override public Codec<dev.justin.infinium.SetLook> valueCodec() { return dev.justin.infinium.SetLook.CODEC; }
    @Override public SelectItemModelProperty.Type<HolderStyleProperty, dev.justin.infinium.SetLook> type() { return TYPE; }
}
