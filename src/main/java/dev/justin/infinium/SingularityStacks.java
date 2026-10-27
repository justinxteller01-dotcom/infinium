package dev.justin.infinium;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Full Singularity (x2) / Infinity (x3, 13.6) set perk: stackable items stack STACK_MULTIPLIER x higher in the player's own main inventory,
 * hotbar and offhand (64 -> 128, 16 -> 32, non-damageable unstackables 1 -> 4; damageable items stay 1). Containers and armor slots are unchanged.
 * Same rule on client and server (both read the synced equipment + infinium:style), so slot math agrees.
 * Set broken: every tick, oversized stacks are split back to the normal limit; the excess is merged into other
 * inventory slots, and whatever does not fit is dropped at the player's feet. Nothing is deleted.
 */
public final class SingularityStacks {
    public static final int STACK_MULTIPLIER = 2;
    /** Normally-unstackable, non-damageable items (merge only when all components match - vanilla rule). */
    public static final int UNSTACKABLE_LIMIT = 4;
    /** Give a by-product (e.g. empty bucket) to the player; drop it at their feet if the inventory is full. */
    public static void give(Player p, ItemStack s) { if (!p.getInventory().add(s) && !s.isEmpty()) p.drop(s, false); }
    public static boolean boostedSlot(int containerSlot) { return containerSlot < Inventory.INVENTORY_SIZE || containerSlot == Inventory.SLOT_OFFHAND; }
    /** Infinity set (13.6): x4 (64->256, 16->64), unstackables 16. Singularity: x2 / 4. Gated by the current set's stacks perk + baseline inheritance. */
    public static int limit(Player p, ItemStack s) {
        int normal = s.getMaxStackSize();
        if (p == null) return normal;
        if (s.is(DungeonFinder.ITEM)) return 1;              // Dungeon Finder compass never stacks above 1
        SetLook l = SetLook.of(p);
        Perk sp = Perk.set(l, "stacks");
        if (sp == null || !Perks.on(p, sp)) return normal;
        if (normal <= 1 && s.isDamageableItem()) return 1;   // tools / armor / anything with durability never stack
        PerkValues.Family f = PerkValues.effectiveFamily(p, l, "stacks");
        return f == null ? normal : Math.max(normal, PerkValues.boostedMax(f, normal));   // R2: Astral 96/24/2, Singularity 128/32/4, Infinity 256/64/16
    }
    /** Every tick: any boosted slot above the CURRENT limit (set removed, Infinity -> Singularity, perk off) is split; the excess
     *  merges into other slots, the rest drops at the player's feet. Nothing is deleted. */
    private static final java.util.Map<java.util.UUID, Integer> LAST = new java.util.HashMap<>();
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!p.isAlive()) continue;
                splitInventory(p);
                // R4: set lost without the UI (death, dispenser, command, ...): also split every carried backpack, no prompt
                SetLook l = SetLook.of(p);
                int key = ArmorRemoval.buffed(p) ? l.ordinal() : -1;
                Integer old = LAST.put(p.getUUID(), key);
                boolean drop = old != null && old != key;
                if (!drop && key < 0 && (server.getTickCount() + p.getId()) % 200 == 0) drop = true;   // backpacks picked up later
                if (drop && ArmorRemoval.BACKPACK_PASS != null) try { ArmorRemoval.BACKPACK_PASS.accept(p); } catch (Throwable ignored) {}
            }
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> LAST.remove(h.player.getUUID()));
    }
    /** Any boosted slot above the CURRENT limit (set removed, Infinity -> Singularity, perk off) is split; the excess merges into
     *  other slots, the rest drops at the player's feet. Nothing is deleted. */
    public static void splitInventory(ServerPlayer p) {
        Inventory inv = p.getInventory();
        boolean changed = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!boostedSlot(i)) continue;
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            int max = limit(p, s);
            if (s.getCount() <= max) continue;
            ItemStack excess = s.split(s.getCount() - max);
            inv.add(excess);
            if (!excess.isEmpty()) p.drop(excess, false);
            changed = true;
        }
        if (changed) { inv.setChanged(); p.inventoryMenu.broadcastChanges(); p.containerMenu.broadcastChanges(); }
    }
    // ---- split-on-change for doubled unstackables (server thread only) ----
    private static net.minecraft.server.MinecraftServer SERVER; private static Thread SERVER_THREAD;
    public static void registerLifecycle() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(s -> { SERVER = s; SERVER_THREAD = Thread.currentThread(); });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> { SERVER = null; SERVER_THREAD = null; });
    }
    private record Pending(Player owner, ItemStack copy) {}
    private static final ThreadLocal<java.util.ArrayDeque<java.util.Optional<Pending>>> PENDING = ThreadLocal.withInitial(java.util.ArrayDeque::new);
    /** Find the player whose inventory slot or cursor holds exactly this stack instance. */
    private static Player ownerOf(ItemStack s) {
        if (SERVER == null) return null;
        for (ServerPlayer p : SERVER.getPlayerList().getPlayers()) {
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i) == s) return p;
            if (p.containerMenu.getCarried() == s) return p;
        }
        return null;
    }
    /** Before a component/damage change: a stack of 2+ normally-unstackable items keeps 1 (which receives the change);
     *  the unchanged rest is handed back to the owner after the change (afterChange). Without an owner nothing is split. */
    public static void beforeChange(ItemStack s) {
        if (s.is(DungeonFinder.ITEM)) return;
        java.util.ArrayDeque<java.util.Optional<Pending>> q = PENDING.get();
        Pending pd = null;
        if (s.getCount() > 1 && Thread.currentThread() == SERVER_THREAD && s.getMaxStackSize() == 1) {
            Player owner = ownerOf(s);
            if (owner != null) { pd = new Pending(owner, s.copyWithCount(s.getCount() - 1)); s.setCount(1); }
        }
        q.push(java.util.Optional.ofNullable(pd));
    }
    public static void afterChange() {
        java.util.ArrayDeque<java.util.Optional<Pending>> q = PENDING.get();
        if (q.isEmpty()) return;
        q.pop().ifPresent(pd -> give(pd.owner(), pd.copy()));   // into any free/matching slot, else at the feet
    }
    private SingularityStacks() {}
}
