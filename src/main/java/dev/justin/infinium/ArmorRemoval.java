package dev.justin.infinium;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.core.component.DataComponents;

/** R4: removing armor while a stack-buff set is worn is confirmed first; the server validates and performs the exact action,
 *  then splits overflow in the inventory and every carried backpack (excess drops at the feet, never deleted). */
public final class ArmorRemoval {
    /** Set by the Traveler's Backpack compat (null when that mod is absent): split worn + inventory backpacks. */
    public static java.util.function.Consumer<ServerPlayer> BACKPACK_PASS = null;

    public static boolean buffed(Player p) {
        SetLook l = SetLook.of(p);
        Perk sp = l == null ? null : Perk.set(l, "stacks");
        return sp != null && Perks.on(p, sp);
    }
    public static boolean isArmorSlot(Slot s) {
        if (s == null || !(s.container instanceof Inventory)) return false;
        int c = s.getContainerSlot();
        return c >= Inventory.INVENTORY_SIZE && c < Inventory.INVENTORY_SIZE + 4;
    }
    /** Hotbar right-click that would swap out a worn armor piece. */
    public static boolean swapsArmor(Player p, ItemStack held) {
        Equippable eq = held.get(DataComponents.EQUIPPABLE);
        if (eq == null || !eq.swappable() || eq.slot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return false;
        return !p.getItemBySlot(eq.slot()).isEmpty();
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(ArmorRemovePayload.TYPE, ArmorRemovePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ArmorRemovePayload.TYPE, (pl, ctx) -> {
            ServerPlayer p = ctx.player();
            if (p.isSpectator() || !p.isAlive()) return;
            if (pl.hand() >= 0) {
                if (pl.hand() > 1) return;
                InteractionHand h = InteractionHand.values()[pl.hand()];
                ItemStack held = p.getItemInHand(h);
                if (!swapsArmor(p, held)) return;
                held.use(p.level(), p, h);
            } else {
                AbstractContainerMenu m = p.containerMenu;
                if (m.containerId != pl.containerId() || pl.slot() < 0 || pl.slot() >= m.slots.size()) return;
                if (pl.input() < 0 || pl.input() >= ContainerInput.values().length) return;
                ContainerInput in = ContainerInput.values()[pl.input()];
                if (!isArmorSlot(m.getSlot(pl.slot())) || in == ContainerInput.QUICK_CRAFT || in == ContainerInput.CLONE) return;
                m.clicked(pl.slot(), pl.button(), in, p);
                m.broadcastFullState();
            }
            splitAll(p);
        });
    }
    /** Inventory + all carried backpacks, against the CURRENT limits. */
    public static void splitAll(ServerPlayer p) {
        SingularityStacks.splitInventory(p);
        if (BACKPACK_PASS != null) try { BACKPACK_PASS.accept(p); } catch (Throwable ignored) {}
    }
    private ArmorRemoval() {}
}
