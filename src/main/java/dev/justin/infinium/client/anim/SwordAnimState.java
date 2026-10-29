package dev.justin.infinium.client.anim;

import dev.justin.infinium.Infinium;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side per-entity animation clocks for Infinium swords.
 *
 * Sync rule: the move and its clock are set by {@link #recordEmittedSlash}, called from the one
 * place every slash/beam payload is sent. A vanilla swing() in the same click only starts a PLAIN
 * swing when no slash was emitted and no slash animation is still playing, so a swing can never
 * show a different move than the beam that was sent. There is no independent combo counter here.
 */
public final class SwordAnimState {
    private SwordAnimState() {}

    private static final class Track {
        float start = -1000F;
        SwordMove move = SwordMove.PLAIN;
        float roll;
        float dur = 8F, twirlDur = 0F;
        int emitTick = Integer.MIN_VALUE;
        float equipStart = -1000F;
        boolean held;
    }

    private static final Map<LivingEntity, Track> TRACKS = new WeakHashMap<>();

    public static boolean isSword(ItemStack s) { return Infinium.SWORD != null && s != null && s.is(Infinium.SWORD); }

    /** Main-hand Infinium sword and our animator drives it. */
    public static boolean active(LivingEntity e) {
        return e != null && SwordAnimTuning.drivesSword() && isSword(e.getItemInHand(InteractionHand.MAIN_HAND));
    }

    private static Track track(LivingEntity e) { return TRACKS.computeIfAbsent(e, k -> new Track()); }

    public static float clock(LivingEntity e, float partialTick) { return e.tickCount + partialTick; }

    public static float duration(SwordMove m) {
        return switch (m) {
            case LEFT_CUT, RIGHT_CUT -> SwordAnimTuning.CUT_TICKS;
            case OVERHEAD_CLEAVE -> SwordAnimTuning.CLEAVE_TICKS;
            case FLAT_SWEEP -> SwordAnimTuning.SWEEP_TICKS;
            case STAB -> SwordAnimTuning.STAB_TICKS;
            case TWIRL -> dev.justin.infinium.SwordCombo.TWIRL_TICKS;
            case PLAIN -> SwordAnimTuning.PLAIN_TICKS;
        };
    }

    /** Called right after a slash payload is sent. Always (re)starts the swing with the emitted move. */
    public static void recordEmittedSlash(LivingEntity e, float roll, boolean isStab) {
        Track t = track(e);
        t.move = SwordMove.fromPayload(roll, isStab);
        t.roll = isStab ? 0F : roll;
        t.start = e.tickCount;
        t.emitTick = e.tickCount;
        // Length follows the attack-strength delay (attack-speed bonuses), so a swing never clips into the next one.
        if (e instanceof net.minecraft.world.entity.player.Player p) {
            float cut = dev.justin.infinium.SwordCombo.cutTicks(p);
            t.dur = isStab ? dev.justin.infinium.SwordCombo.stabTicks(p)
                    : (t.move == SwordMove.OVERHEAD_CLEAVE ? cut * (SwordAnimTuning.CLEAVE_TICKS / SwordAnimTuning.CUT_TICKS) : cut);
            t.twirlDur = isStab ? dev.justin.infinium.SwordCombo.twirlTicks(p) : 0F;
        } else {
            t.dur = duration(t.move);
            t.twirlDur = isStab ? dev.justin.infinium.SwordCombo.TWIRL_TICKS : 0F;
        }
    }

    /** Called from LivingEntity.swing (client) for every swing, emitted or not. */
    public static void onSwing(LivingEntity e, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !active(e)) return;
        Track t = track(e);
        int now = e.tickCount;
        if (now - t.emitTick <= 1) return;                       // this click already emitted: keep that move
        float age = now - t.start;
        if (t.move != SwordMove.PLAIN && age < t.dur) return; // don't cut a slash animation short
        if (t.move == SwordMove.PLAIN && age < SwordAnimTuning.PLAIN_TICKS * 0.5F) return;
        t.move = SwordMove.PLAIN;
        t.roll = 0F;
        t.start = now;
        t.dur = SwordAnimTuning.PLAIN_TICKS;
        t.twirlDur = 0F;
    }

    public static SwordMove move(LivingEntity e) { Track t = TRACKS.get(e); return t == null ? SwordMove.PLAIN : t.move; }
    public static float roll(LivingEntity e) { Track t = TRACKS.get(e); return t == null ? 0F : t.roll; }

    /** 0..1 progress of the current move, or 0 when idle. */
    public static float swingProgress(LivingEntity e, float partialTick) {
        Track t = TRACKS.get(e);
        if (t == null) return 0F;
        float age = clock(e, partialTick) - t.start;
        if (t.move == SwordMove.STAB && age >= t.dur && t.twirlDur > 0F) {   // stab flows straight into the spin return
            t.start += t.dur;
            t.dur = t.twirlDur;
            t.twirlDur = 0F;
            t.move = SwordMove.TWIRL;
            age = clock(e, partialTick) - t.start;
        }
        float p = age / t.dur;
        return (p <= 0F || p >= 1F) ? 0F : p;
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
