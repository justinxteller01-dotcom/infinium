package dev.justin.infinium;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/** infinium:style - which glow animation an Infinium item uses. Set only by the style smithing template used for the
 *  netherite -> Infinium upgrade; dye recolouring keeps it. Absent (items made before templates existed) = legacy look. */
public enum InfiniumStyle implements StringRepresentable {
    ASTRAL("astral", 40, 300, false),            // preview v08
    CYBERNETIC("cybernetic", 40, 300, false),    // preview v09
    SINGULARITY("singularity", 100, 100, true),   // preview v11b (animated name)
    CYBERPSYCHO("cyberpsycho", 80, 100, true);   // preview v12 (animated name)

    public final String id; public final int frames, frameMs; public final boolean rarer;
    InfiniumStyle(String id, int frames, int frameMs, boolean rarer) { this.id = id; this.frames = frames; this.frameMs = frameMs; this.rarer = rarer; }
    @Override public String getSerializedName() { return id; }

    public static final StringRepresentable.EnumCodec<InfiniumStyle> CODEC = StringRepresentable.fromEnum(InfiniumStyle::values);
    public static DataComponentType<InfiniumStyle> STYLE;
    public static void register() {
        STYLE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Infinium.id("style"),
            DataComponentType.<InfiniumStyle>builder().persistent(CODEC)
                .networkSynchronized(ByteBufCodecs.VAR_INT.map(i -> values()[Math.floorMod(i, values().length)], Enum::ordinal)).build());
    }
    public static InfiniumStyle get(ItemStack s) { return s == null || STYLE == null ? null : s.get(STYLE); }
}
