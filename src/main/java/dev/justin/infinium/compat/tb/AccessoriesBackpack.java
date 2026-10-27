package dev.justin.infinium.compat.tb;

import com.tiviacz.travelersbackpack.item.TravelersBackpackItem;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Only touched when the Accessories mod is loaded (class loading is lazy). */
final class AccessoriesBackpack {
    private static final Predicate<ItemStack> IS_BACKPACK = s -> s.getItem() instanceof TravelersBackpackItem;
    static ItemStack find(Player p) {
        AccessoriesCapability cap = AccessoriesCapability.get(p);
        if (cap == null) return ItemStack.EMPTY;
        List<SlotEntryReference> l = cap.getEquipped(IS_BACKPACK);
        return l == null || l.isEmpty() ? ItemStack.EMPTY : l.get(0).stack();
    }
    private AccessoriesBackpack() {}
}
