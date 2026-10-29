package dev.justin.infinium;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Shared (client + server) timing for the Infinium sword combo: left slash -> right slash -> stab -> twirl lockout.
 * Everything is in game ticks and derived from the player's live ATTACK_SPEED attribute, so the
 * Cybernetic / Cyber Psycho attack-speed bonuses shorten the whole chain together and nothing clips.
 * No client imports.
 */
public final class SwordCombo {
    private SwordCombo() {}

    /** Vanilla sword attack speed (1.6/s): all base timings below are tuned at this speed. */
    public static final double BASE_ATTACK_SPEED = 1.6;
    /** Click-to-stab window after the second swing, at base attack speed (2 s). */
    public static final int STAB_WINDOW_TICKS = 40;
    /** Stab window never shrinks below this with attack-speed bonuses (1.6 s). */
    public static final int STAB_WINDOW_MIN_TICKS = 32;
    /** Twirl (spin return) after the stab, at base attack speed. Also the post-stab cooldown. */
    public static final float TWIRL_TICKS = 22F;
    /** Fraction of the attack-strength delay each swing animation fills (rest is settle time, so no clipping). */
    public static final float CUT_FILL = 0.94F, STAB_FILL = 0.90F;
    /** Server tolerance for packet bunching (client ticks vs server receive ticks). */
    public static final int SERVER_SLACK_TICKS = 3;

    public static double attackSpeed(Player p) {
        double s = p.getAttributeValue(Attributes.ATTACK_SPEED);
        return s > 0.05 ? s : BASE_ATTACK_SPEED;
    }
    /** Speed factor: 1 at base speed, below 1 when faster (bonuses). Clamped so animations stay readable. */
    public static float speedScale(Player p) {
        return (float) Math.max(0.45, Math.min(1.0, BASE_ATTACK_SPEED / attackSpeed(p)));
    }
    /** Vanilla attack-strength delay in ticks (full charge time). */
    public static float delayTicks(Player p) { return (float) (20.0 / attackSpeed(p)); }
    public static float cutTicks(Player p) { return delayTicks(p) * CUT_FILL; }
    public static float stabTicks(Player p) { return delayTicks(p) * STAB_FILL; }
    public static float twirlTicks(Player p) { return TWIRL_TICKS * speedScale(p); }
    /** Lockout after the stab click: stab animation plus the twirl. Next combo starts after it. */
    public static int postStabLockTicks(Player p) { return (int) Math.ceil(stabTicks(p) + twirlTicks(p)); }
    /** 2 s window, shrinks slightly (sqrt) with attack-speed bonuses, never below 1.6 s. */
    public static int stabWindowTicks(Player p) {
        return Math.max(STAB_WINDOW_MIN_TICKS, Math.round(STAB_WINDOW_TICKS * (float) Math.sqrt(speedScale(p))));
    }
    /** Minimum ticks between any two beams (server rate limit), before slack. */
    public static int minIntervalTicks(Player p) { return Math.max(3, (int) Math.floor(delayTicks(p)) - 1); }
}
