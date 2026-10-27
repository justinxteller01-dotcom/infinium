package dev.justin.infinium.compat;

import dev.justin.infinium.SetBonuses;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** pass2: set auto-repair for items in Accessories slots, via the public API (SlotReference.setStack). Only class-loaded when
 *  Accessories is present; every call is fail-safe. A backpack worn there is handled by TbStacks (its own item has no durability). */
public final class AccessoriesRepair {
    public static void register() { SetBonuses.REPAIR_HOOKS.add(AccessoriesRepair::repair); }
    static boolean repair(ServerPlayer p) {
        AccessoriesCapability cap = AccessoriesCapability.get(p);
        if (cap == null) return false;
        boolean ch = false;
        for (SlotEntryReference e : cap.getAllEquipped()) {
            ItemStack c = SetBonuses.repairedCopy(e.stack());
            if (c != null && e.reference().isValid() && e.reference().setStack(c)) ch = true;
        }
        return ch;
    }
    private AccessoriesRepair() {}
}
