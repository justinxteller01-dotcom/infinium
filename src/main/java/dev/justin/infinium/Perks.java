package dev.justin.infinium;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Server-authoritative perk toggles (R8 13.14 B). One long bitmask of DISABLED perks per player (0 = all on), saved with a Fabric
 * data attachment (persistent, copyOnDeath) and synced to the owning client only. Mobs / armor stands: always all on.
 */
public final class Perks {
    public static AttachmentType<Long> MASK;
    public static void register() {
        MASK = AttachmentRegistry.<Long>create(Infinium.id("perks"), b -> b.persistent(Codec.LONG).copyOnDeath().initializer(() -> 0L)
            .syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));
        PayloadTypeRegistry.serverboundPlay().register(PerkTogglePayload.TYPE, PerkTogglePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PerkMaskPayload.TYPE, PerkMaskPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PerkTogglePayload.TYPE, (pl, ctx) -> handle(ctx.player(), pl));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> LAST.remove(h.player.getUUID()));
    }
    private static final java.util.Map<java.util.UUID, Long> LAST = new java.util.concurrent.ConcurrentHashMap<>();
    public static long mask(Player p) { Long m = ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) (Object) p).getAttached(MASK); return m == null ? 0L : m; }
    /** Single gate. null perk = not toggleable (always on). */
    public static boolean on(LivingEntity e, Perk p) {
        if (p == null || !(e instanceof Player pl) || MASK == null) return true;
        if ((mask(pl) & p.bit()) != 0) return false;
        // Crystal power gates the set perks in CrystalCharge.ORDER only. Piece perks, fire/lava,
        // explosion, damage taken, and leggings speed stay on without crystals.
        if (p.isSet() && CrystalCharge.gated(p.kind) && !CrystalCharge.unlocked(pl, p)) return false;
        return true;
    }
    static void handle(ServerPlayer sp, PerkTogglePayload pl) {
        if (pl.perk() < 0 || pl.perk() >= Perk.values().length) return;
        long now = sp.level().getGameTime();
        Long last = LAST.get(sp.getUUID());
        if (last != null && now - last < 2) { resync(sp); return; }   // M5: 1 per 2 ticks; a dropped toggle resyncs so the client reverts at once
        Perk p = Perk.values()[pl.perk()];
        boolean on = (pl.mode() & 1) == 1, all = (pl.mode() & 2) == 2;
        if (p.kind.equals("stacks") && !on) {          // stack-size toggle: 20-tick cooldown against churn
            if (last != null && now - last < 20) { resync(sp); return; }
        }
        LAST.put(sp.getUUID(), now);
        long m = mask(sp);
        for (Perk q : Perk.values()) {
            if (!(q == p || (all && q.kind.equals(p.kind) && q.isSet() == p.isSet()))) continue;
            m = on ? m & ~q.bit() : m | q.bit();
        }
        ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) (Object) sp).setAttached(MASK, m);
        SetBonuses.markDirty(sp);
        resync(sp);   // M5: explicit ack so the client clears its pending state immediately
        String label = Component.translatable(p.translationKey()).getString();
        sp.sendOverlayMessage(Component.literal((all ? "All styles" : p.familyName()) + " · " + label + ": ")
            .withStyle(ChatFormatting.LIGHT_PURPLE)
            .append(Component.literal(on ? "On" : "Off").withStyle(on ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY)));
    }
    /** M5: tell the client the authoritative mask even when unchanged (the attachment only syncs on change). */
    static void resync(ServerPlayer sp) {
        if (ServerPlayNetworking.canSend(sp, PerkMaskPayload.TYPE)) ServerPlayNetworking.send(sp, new PerkMaskPayload(mask(sp)));
    }
    private Perks() {}
}
