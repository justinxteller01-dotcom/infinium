package dev.justin.infinium;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * batch1 A1/A3: one-way, per-player UI unlock flags. Saved server-side on the player (persistent, copyOnDeath) and
 * synced to the owning client only, so they survive relogs, deaths and dimension changes. Bits never clear.
 * - {@link #GEAR}: the player has held/worn ANY Infinium weapon, tool or armor piece -> inventory settings gear shows forever.
 * - {@link #FULL_SET}: the player has equipped a full matching Infinium set once -> "x/80" crystal counter shows forever.
 * Checked every {@link #CHECK_INTERVAL} ticks (cheap: one inventory scan per player per second).
 */
public final class UiUnlocks {
    public static final int GEAR = 1, FULL_SET = 2;
    static final int CHECK_INTERVAL = 20;
    public static AttachmentType<Integer> FLAGS;

    public static void register() {
        FLAGS = AttachmentRegistry.<Integer>create(Infinium.id("ui_unlocks"), b -> b.persistent(Codec.INT).copyOnDeath().initializer(() -> 0)
            .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
        ServerTickEvents.END_SERVER_TICK.register(srv -> {
            if (srv.getTickCount() % CHECK_INTERVAL != 0) return;
            for (ServerPlayer p : srv.getPlayerList().getPlayers()) update(p);
        });
    }

    public static int flags(Player p) {
        if (p == null || FLAGS == null) return 0;
        Integer v = ((AttachmentTarget) (Object) p).getAttached(FLAGS);
        return v == null ? 0 : v;
    }

    public static boolean has(Player p, int bit) { return (flags(p) & bit) != 0; }

    static void update(ServerPlayer p) {
        int f = flags(p), n = f;
        if ((n & GEAR) == 0 && hasAnyGear(p)) n |= GEAR;
        if ((n & FULL_SET) == 0 && Infinium.setWorn(p)) n |= FULL_SET | GEAR;
        if (n != f) ((AttachmentTarget) (Object) p).setAttached(FLAGS, n);
    }

    static boolean hasAnyGear(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (isGear(inv.getItem(i))) return true;
        for (EquipmentSlot sl : EquipmentSlot.values()) if (isGear(p.getItemBySlot(sl))) return true;
        return isGear(p.containerMenu == null ? ItemStack.EMPTY : p.containerMenu.getCarried());
    }

    static boolean isGear(ItemStack s) { return s != null && !s.isEmpty() && (Infinium.isArmor(s) || Infinium.isTool(s)); }

    private UiUnlocks() {}
}
