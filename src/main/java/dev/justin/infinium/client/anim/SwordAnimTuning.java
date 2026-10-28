package dev.justin.infinium.client.anim;

import dev.justin.infinium.compat.punchy.PunchyBridge;

/**
 * Every tunable value for Infinium's own sword animations lives here (client-only).
 * Angles are degrees, distances are first-person view units (vanilla hand space), times are ticks (20/s).
 *
 * Kill switch without rebuilding: add the JVM argument -Dinfinium.swordAnim=false
 * (then Infinium swords go back to Punchy / vanilla exactly as before this feature).
 */
public final class SwordAnimTuning {
    private SwordAnimTuning() {}

    // ---- master switches ----
    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("infinium.swordAnim", "true"));
    /** When Punchy is installed, Infinium swords use these animations instead of Punchy's clips. */
    public static final boolean OVERRIDE_PUNCHY = true;
    /** Alternate swing direction each hit (forehand / backhand). */
    public static final boolean SWING_ALTERNATE = true;

    // ---- first-person base hold (added on top of vanilla's sword hold) ----
    public static final float FP_BASE_X = 0.56F, FP_BASE_Y = -0.52F, FP_BASE_Z = -0.72F; // vanilla arm offset
    public static final float FP_HOLD_PITCH = 0F, FP_HOLD_YAW = 0F, FP_HOLD_ROLL = 0F;

    // ---- swing / attack ----
    public static final float SWING_TICKS = 8.0F;      // whole swing incl. follow-through
    public static final float WINDUP_END = 0.16F;      // fraction of swing spent pulling back
    public static final float STRIKE_END = 0.52F;      // fraction where the main arc finishes
    public static final float WINDUP_YAW = -22F, WINDUP_PITCH = 12F, WINDUP_ROLL = -10F, WINDUP_BACK = 0.06F;
    public static final float STRIKE_YAW = 62F, STRIKE_PITCH = -38F, STRIKE_ROLL = 34F;
    public static final float STRIKE_FORWARD = 0.18F, STRIKE_DROP = 0.10F, STRIKE_SIDE = 0.22F;
    /** Trailing follow-through: damped overshoot after the arc, then settles back to rest. */
    public static final float FOLLOW_OVERSHOOT = 0.14F, FOLLOW_DAMPING = 5.5F, FOLLOW_FREQ = 2.2F;

    // ---- idle sway / breathing ----
    public static final float IDLE_BREATH_PERIOD = 70F;  // ticks per breath
    public static final float IDLE_BREATH_Y = 0.012F;
    public static final float IDLE_SWAY_PERIOD = 113F;
    public static final float IDLE_SWAY_X = 0.008F, IDLE_SWAY_ROLL = 1.4F, IDLE_SWAY_PITCH = 0.9F;
    public static final float IDLE_WALK_SCALE = 0.4F;     // idle amplitude kept while walking (vanilla bob takes over)
    public static final float CAMERA_LAG_SCALE = 0.1F;    // vanilla-style hand lag vs. camera turn

    // ---- equip flourish ----
    public static final float EQUIP_TICKS = 12F;
    public static final float EQUIP_RISE = 0.45F;        // starts this far below rest
    public static final float EQUIP_SPIN = -300F;        // roll degrees wound in at the start, unwound to 0
    public static final float EQUIP_TILT = 25F;

    // ---- third-person arm pose (radians) ----
    public static final float TP_HOLD_PITCH = -0.32F, TP_HOLD_YAW = -0.10F;
    public static final float TP_WINDUP_PITCH = -2.4F, TP_WINDUP_YAW = 0.55F;
    public static final float TP_STRIKE_PITCH = -0.25F, TP_STRIKE_YAW = -0.75F, TP_STRIKE_ROLL = 0.35F;
    public static final float TP_IDLE_PITCH = 0.05F;
    public static final float TP_EQUIP_PITCH = -1.9F;

    /** True when Infinium should take over animations of its swords from Punchy. */
    public static boolean overridesPunchy() {
        return ENABLED && OVERRIDE_PUNCHY && PunchyBridge.isPunchyLoaded();
    }
}
