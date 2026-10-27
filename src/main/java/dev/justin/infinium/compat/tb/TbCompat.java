package dev.justin.infinium.compat.tb;

import com.tiviacz.travelersbackpack.TravelersBackpack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Traveler's Backpack 11.3.3 (Fabric) only looks for a worn backpack through the Trinkets API. With Accessories installed
 * (and no Trinkets) AttachmentUtils.isWearingBackpack() is always false, so every backpack hotkey does nothing while the
 * backpack sits in the Accessories "back" slot. We answer from the Accessories slot instead. No config or keybind is touched.
 */
public final class TbCompat {
    private static final boolean ACCESSORIES = FabricLoader.getInstance().isModLoaded("accessories");
    private static final boolean TRINKETS = FabricLoader.getInstance().isModLoaded("trinkets") || FabricLoader.getInstance().isModLoaded("trinkets_updated");

    /** The backpack in the Accessories slot, or EMPTY when that path does not apply. */
    public static ItemStack accessoryBackpack(Player p) {
        if (!ACCESSORIES || TRINKETS || p == null) return ItemStack.EMPTY;
        try {
            if (!TravelersBackpack.enableAccessories()) return ItemStack.EMPTY;   // TB's own back-slot integration is off: TB's slot is used
            return AccessoriesBackpack.find(p);
        } catch (Throwable t) { return ItemStack.EMPTY; }                     // config not loaded yet / API change: fall back to TB
    }
    private TbCompat() {}
}
