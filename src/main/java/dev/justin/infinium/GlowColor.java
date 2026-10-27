package dev.justin.infinium;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/** infinium:glow_color - dye colour of the Infinium glow (absent = default purple). Set by smithing: item + dye, no template. */
public final class GlowColor {
    public static DataComponentType<DyeColor> GLOW_COLOR;
    /** Highlight RGB per DyeColor id (dye hue at full brightness; white = silver-white, black = dark smoke). Mirrors gen_dye.py. */
    public static final int[] RGB = {0xE8ECFF, 0xFF831D, 0xFF63F2, 0x43D1FF, 0xFFD83D, 0xA4FF27, 0xFF91B2, 0x9A9AA6,
                                     0xC8C8D2, 0x23FFFF, 0xBD45FF, 0x5A66FF, 0xFFA361, 0xC1FF2D, 0xFF4237, 0x5A5866};
    public static void register() {
        GLOW_COLOR = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Infinium.id("glow_color"),
            DataComponentType.<DyeColor>builder().persistent(DyeColor.CODEC).networkSynchronized(DyeColor.STREAM_CODEC).build());
    }
    public static DyeColor get(ItemStack s) { return s == null ? null : s.get(GLOW_COLOR); }
    /** ARGB tint for the grayscale glow textures, or -1 when the default purple textures should be used. */
    public static int argb(ItemStack s) { DyeColor c = get(s); return c == null ? -1 : 0xFF000000 | RGB[c.getId()]; }
    /** Smithing: the dye in the template slot always wins over a colour copied from the base item. */
    public static void applyFromTemplate(ItemStack out, ItemStack template) {
        DyeColor d = template.get(DataComponents.DYE);
        if (d != null && out.has(GLOW_COLOR)) out.set(GLOW_COLOR, d);
    }
    private GlowColor() {}
}
