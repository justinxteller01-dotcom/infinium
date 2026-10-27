package dev.justin.infinium.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.justin.infinium.GlowColor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** infinium:preview_color - for undyed Infinium items drawn as "fake" GUI items (no holder: JEI list / recipe slots),
 *  cycles through the 16 dye glow colours (0.75 s each). Real inventory/creative/worn/held items always have a holder -> null. */
public final class PreviewColorProperty implements SelectItemModelProperty<DyeColor> {
    public static final PreviewColorProperty INSTANCE = new PreviewColorProperty();
    public static final SelectItemModelProperty.Type<PreviewColorProperty, DyeColor> TYPE =
        SelectItemModelProperty.Type.create(MapCodec.unit(INSTANCE), DyeColor.CODEC);
    public static final long STEP_MS = 750;
    @Override public DyeColor get(ItemStack stack, ClientLevel level, LivingEntity owner, int seed, ItemDisplayContext ctx) {
        return null;
    }
    @Override public Codec<DyeColor> valueCodec() { return DyeColor.CODEC; }
    @Override public SelectItemModelProperty.Type<PreviewColorProperty, DyeColor> type() { return TYPE; }
}
