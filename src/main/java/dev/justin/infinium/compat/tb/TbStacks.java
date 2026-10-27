package dev.justin.infinium.compat.tb;
import dev.justin.infinium.compat.mixin.tb.StorageOwnerAccessor;

import com.tiviacz.travelersbackpack.attachment.AttachmentUtils;
import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import com.tiviacz.travelersbackpack.inventory.handler.ItemStackHandler;
import dev.justin.infinium.SingularityStacks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Stack sizes inside Traveler's Backpack storage follow the carrier's full set (Astral 96/24/2, Singularity 128/32/4,
 * Infinity 256/64/16). Saving: TB stores the storage as ItemContainerContents (ItemStackTemplate, count codec widened to
 * 1..1024 by ItemStackTemplateCodecMixin), so oversized stacks persist. When the set or the perk goes away, the worn
 * backpack is checked once a second: overflow merges into other backpack slots, then the player inventory, then drops.
 */
public final class TbStacks {
    static Player owner(Object handler) {
        if (!(handler instanceof StorageOwnerAccessor acc)) return null;
        BackpackWrapper w = acc.infinium$wrapper();
        if (w == null || w.backpackPos != null) return null;                // placed backpack block: vanilla limits
        Player p = w.getBackpackOwner();
        if (p == null && w.getPlayersUsing() != null && !w.getPlayersUsing().isEmpty()) p = w.getPlayersUsing().get(0);
        return p;
    }
    public static int limit(Object handler, ItemStack s) {
        Player p = owner(handler);
        return p == null || s.isEmpty() ? 0 : SingularityStacks.limit(p, s);
    }

    public static void register() {
        dev.justin.infinium.ArmorRemoval.BACKPACK_PASS = TbStacks::splitAllBackpacks;
        dev.justin.infinium.SetBonuses.REPAIR_HOOKS.add(TbStacks::repairAllBackpacks);   // pass2
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, srv) -> forget(h.player.getUUID()));   // M6
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 7) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                try {
                    if (!AttachmentUtils.isWearingBackpack(p)) continue;
                    BackpackWrapper w = AttachmentUtils.getBackpackWrapper(p);
                    if (w != null) split(p, w.getStorage());
                } catch (Throwable ignored) {}                           // never break the tick for a compat check
            }
        });
    }
    /** Worn (TB slot or Accessories) + every backpack item in the player inventory. */
    static void splitAllBackpacks(ServerPlayer p) {
        if (AttachmentUtils.isWearingBackpack(p)) { BackpackWrapper w = AttachmentUtils.getBackpackWrapper(p); if (w != null) split(p, w.getStorage()); }
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!(s.getItem() instanceof com.tiviacz.travelersbackpack.item.TravelersBackpackItem)) continue;
            BackpackWrapper w = BackpackWrapper.getBackpackWrapper(p, s, new int[]{BackpackWrapper.STORAGE_ID});
            if (w != null) split(p, w.getStorage());
        }
    }
    /** pass2: set auto-repair inside the worn backpack and every backpack item in the player inventory (storage + tool slots).
     *  Writes go through ItemStackHandler.setStackInSlot so TB saves them to the backpack. Placed backpacks are not touched. */
    /** M6: per player, per inventory slot, the (storage, tools) contents instances last found fully repaired. */
    private static final java.util.Map<java.util.UUID, Object[]> CLEAN = new java.util.concurrent.ConcurrentHashMap<>();
    public static void forget(java.util.UUID id) { CLEAN.remove(id); }
    static boolean repairAllBackpacks(ServerPlayer p) {
        boolean ch = false;
        java.util.IdentityHashMap<Object, Boolean> seen = new java.util.IdentityHashMap<>();
        if (AttachmentUtils.isWearingBackpack(p)) {
            BackpackWrapper w = AttachmentUtils.getBackpackWrapper(p);
            if (w != null) ch |= repairWrapper(w, seen);
        }
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!(s.getItem() instanceof com.tiviacz.travelersbackpack.item.TravelersBackpackItem)) continue;
            try {
                // M6 throttle: the stored contents components are immutable, so if this slot still holds the exact same
                // component instances we last saw with nothing damaged, skip building a wrapper entirely.
                Object bc = s.get(com.tiviacz.travelersbackpack.init.ModDataComponents.BACKPACK_CONTAINER);
                Object tc = s.get(com.tiviacz.travelersbackpack.init.ModDataComponents.TOOLS_CONTAINER);
                Object[] clean = CLEAN.computeIfAbsent(p.getUUID(), k -> new Object[2 * 64]);
                int k2 = 2 * i;
                if (k2 + 1 < clean.length && bc != null && clean[k2] == bc && clean[k2 + 1] == tc) continue;
                BackpackWrapper w = BackpackWrapper.getBackpackWrapper(p, s, new int[]{BackpackWrapper.STORAGE_ID, BackpackWrapper.TOOLS_ID});
                if (w == null) continue;
                boolean r = repairWrapper(w, seen);
                ch |= r;
                if (k2 + 1 < clean.length) {
                    if (r) { clean[k2] = null; clean[k2 + 1] = null; }       // re-check next pulse (more damage may remain)
                    else { clean[k2] = s.get(com.tiviacz.travelersbackpack.init.ModDataComponents.BACKPACK_CONTAINER);
                           clean[k2 + 1] = s.get(com.tiviacz.travelersbackpack.init.ModDataComponents.TOOLS_CONTAINER); }
                }
            } catch (Throwable ignored) {}
        }
        return ch;
    }
    private static boolean repairWrapper(BackpackWrapper w, java.util.IdentityHashMap<Object, Boolean> seen) {
        boolean ch = false;
        ItemStackHandler[] hs;
        try { hs = new ItemStackHandler[]{w.getStorage(), w.getTools()}; } catch (Throwable t) { hs = new ItemStackHandler[]{w.getStorage()}; }
        for (ItemStackHandler h : hs) {
            if (h == null || seen.put(h, Boolean.TRUE) != null) continue;
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack c = dev.justin.infinium.SetBonuses.repairedCopy(h.getStackInSlot(i));
                if (c != null) { h.setStackInSlot(i, c); ch = true; }
            }
        }
        if (ch && w.saveHandler != null) try { w.saveHandler.run(); } catch (Throwable ignored) {}
        return ch;
    }

    static void split(ServerPlayer p, ItemStackHandler h) {
        if (h == null) return;
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty()) continue;
            int max = Math.max(s.getMaxStackSize(), SingularityStacks.limit(p, s));
            if (s.getCount() <= max) continue;
            ItemStack keep = s.copyWithCount(max), excess = s.copyWithCount(s.getCount() - max);
            h.setStackInSlot(i, keep);
            for (int j = 0; j < h.getSlots() && !excess.isEmpty(); j++) if (j != i) excess = h.insertItem(j, excess, false);
            if (!excess.isEmpty()) SingularityStacks.give(p, excess);       // inventory, else dropped at the feet
        }
    }
    private TbStacks() {}
}
