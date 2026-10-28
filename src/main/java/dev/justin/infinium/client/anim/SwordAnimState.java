package dev.justin.infinium.client.anim;

import dev.justin.infinium.Infinium;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side per-entity animation clocks for Infinium swords. Independent of vanilla's
 * attackAnim (which Punchy freezes) so the swing is driven only by our own data.
 * Weak keys: entries vanish with their entities.
 */
public final class SwordAnimState {
    private SwordAnimState() {}

    private static final class Track {
        float swingStart = -1000F;
        int swingCount;
        float equipStart = -1000F;
        boolean held;
    }

    private static final Map<LivingEntity, Track> TRACKS = new WeakHashMap<>();

    public static boolean isSword(ItemStack s) { return Infinium.SWORD != null && s != null && s.is(Infinium.SWORD); }

    /** Main-hand Infinium sword and the feature is on. */
    public static boolean active(LivingEntity e) {
        return SwordAnimTuning.ENABLED && e != null && isSword(e.getItemInHand(InteractionHand.MAIN_HAND));
    }

    private static Track track(LivingEntity e) { return TRACKS.computeIfAbsent(e, k -> new Track()); }

    public static float clock(LivingEntity e, float partialTick) { return e.tickCount + partialTick; }

    /** Called from LivingEntity.swing (client). */
    public static void onSwing(LivingEntity e, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !active(e)) return;
        Track t = track(e);
        float now = e.tickCount;
        // Ignore re-triggers inside the first half of a swing (vanilla re-swings while holding attack).
        if (now - t.swingStart < SwordAnimTuning.SWING_TICKS * 0.5F) return;
        t.swingStart = now;
        t.swingCount++;
    }

    /** 0..1 swing progress, or 0 when not swinging. */
    public static float swingProgress(LivingEntity e, float partialTick) {
        Track t = TRACKS.get(e);
        if (t == null) return 0F;
        float p = (clock(e, partialTick) - t.swingStart) / SwordAnimTuning.SWING_TICKS;
        return (p <= 0F || p >= 1F) ? 0F : p;
    }

    /** +1 / -1: forehand or backhand for the current swing. */
    public static float swingSide(LivingEntity e) {
        Track t = TRACKS.get(e);
        if (t == null || !SwordAnimTuning.SWING_ALTERNATE) return 1F;
        return (t.swingCount & 1) == 0 ? -1F : 1F;
    }

    /** Updates the held-state edge and returns ticks since the sword was drawn. */
    public static float ticksSinceEquip(LivingEntity e, float partialTick) {
        Track t = track(e);
        boolean held = active(e);
        if (held && !t.held) t.equipStart = e.tickCount;
        t.held = held;
        return clock(e, partialTick) - t.equipStart;
    }
}
