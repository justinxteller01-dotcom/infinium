package dev.justin.infinium.client;

import dev.justin.infinium.Perk;
import dev.justin.infinium.PerkMaskPayload;
import dev.justin.infinium.PerkTogglePayload;
import dev.justin.infinium.Perks;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * M5: the ONE client view of the perk/ability toggles, used by both the settings menu (InfiniumPerksScreen) and the
 * matrix (CrystalScreen). Truth = the server's per-player mask (Perks.MASK attachment, persistent, copyOnDeath).
 * A click is applied optimistically (pending) so both screens flip in the same frame, then the server's
 * PerkMaskPayload ack (also sent on a rejected toggle) replaces it. Pending entries expire after 2s as a safety net.
 */
public final class ClientPerks {
    private record Pending(boolean on, long untilMs) {}
    private static final Map<Perk, Pending> PENDING = new EnumMap<>(Perk.class);

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PerkMaskPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            var pl = ctx.client().player;
            if (pl != null && Perks.MASK != null) ((AttachmentTarget) (Object) pl).setAttached(Perks.MASK, p.mask());
            PENDING.clear();
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> PENDING.clear());
    }

    /** Effective disabled mask = server mask + pending local clicks. */
    public static long mask(Player p) {
        long m = p == null ? 0L : Perks.mask(p);
        long now = System.currentTimeMillis();
        PENDING.values().removeIf(x -> x.untilMs() < now);
        for (var e : PENDING.entrySet()) m = e.getValue().on() ? m & ~e.getKey().bit() : m | e.getKey().bit();
        return m;
    }

    /** Toggle state as the player set it (not crystal gating; see CrystalCharge for locked/unlocked). */
    public static boolean enabled(Perk perk) {
        return perk == null || (mask(Minecraft.getInstance().player) & perk.bit()) == 0;
    }

    public static boolean canSend() { return Minecraft.getInstance().player != null && ClientPlayNetworking.canSend(PerkTogglePayload.TYPE); }

    public static void set(Perk perk, boolean on) {
        if (perk == null || !canSend()) return;
        PENDING.put(perk, new Pending(on, System.currentTimeMillis() + 2000));
        ClientPlayNetworking.send(new PerkTogglePayload(perk.ordinal(), (byte) (on ? 1 : 0)));
    }

    private ClientPerks() {}
}
