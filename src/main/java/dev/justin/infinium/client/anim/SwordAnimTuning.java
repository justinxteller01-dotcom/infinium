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

    // ---- first-person base hold (added on top of vanilla's sword hold) ----
    public static final float FP_BASE_X = 0.56F, FP_BASE_Y = -0.52F, FP_BASE_Z = -0.72F; // vanilla arm offset
    public static final float FP_HOLD_PITCH = 0F, FP_HOLD_YAW = 0F, FP_HOLD_ROLL = 0F;

    // ---- swing / attack ----
    public static final float WINDUP_END = 0.16F;      // fraction of swing spent pulling back
    public static final float STRIKE_END = 0.52F;      // fraction where the main arc finishes
    /** Trailing follow-through: damped overshoot after the arc, then settles back to rest. */
    public static final float FOLLOW_OVERSHOOT = 0.14F, FOLLOW_DAMPING = 5.5F, FOLLOW_FREQ = 2.2F;

    // ---- per-move swings (synced to the emitted slash; arc tilt comes from the payload roll itself) ----
    /** Which end of the slash arc axis the blade starts at: +1 = the axis' +X end after rolling by the payload roll. */
    public static final float LEFT_CUT_START = -1F;   // roll -35: +X end is lower-right -> start upper-left
    public static final float RIGHT_CUT_START = 1F;   // roll +35: +X end is upper-right -> start there
    public static final float CLEAVE_START = 1F;      // roll 90: +X end is top -> chop downward
    public static final float SWEEP_START = 1F;       // roll 0: start right, sweep left
    public static final float CUT_TICKS = 8F, CLEAVE_TICKS = 10F, SWEEP_TICKS = 8F, STAB_TICKS = 9F, PLAIN_TICKS = 6F;
    /** First-person: sweep angles (deg) of the arm around the camera, in the slash plane. */
    public static final float ARC_WINDUP_DEG = 38F, ARC_STRIKE_DEG = 52F;
    public static final float CLEAVE_WINDUP_DEG = 48F, CLEAVE_STRIKE_DEG = 40F;
    /** Pull the hand toward screen centre during a slash (0..1 of the side offset). */
    public static final float ARC_CENTER_PULL = 0.55F;
    /** Lay the blade forward so its edge leads along the arc (deg). */
    public static final float ARC_BLADE_LEVEL = -45F;
    /** Roll the blade into the slash plane: multiplier on payload roll applied to the item. */
    public static final float ARC_BLADE_ROLL_FOLLOW = 1.0F;
    // stab
    public static final float STAB_PULLBACK = 0.18F, STAB_THRUST = 0.55F, STAB_CENTER_PULL = 0.75F, STAB_BLADE_LEVEL = -80F, STAB_TWIST = 20F;
    // plain (no slash emitted): small vanilla-like tap
    public static final float PLAIN_YAW = 18F, PLAIN_PITCH = -20F, PLAIN_FORWARD = 0.08F;
    // third person
    public static final float TP_ARC_WINDUP_DEG = 70F, TP_ARC_STRIKE_DEG = 70F, TP_YAW_SIGN = 1F;
    public static final float TP_STAB_PULLBACK = 0.6F, TP_PLAIN_PITCH = -0.8F;

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
    public static final float TP_IDLE_PITCH = 0.05F;
    public static final float TP_EQUIP_PITCH = -1.9F;

    /** True when Infinium's own animator should drive the main-hand Infinium sword. */
    public static boolean drivesSword() {
        return ENABLED && (OVERRIDE_PUNCHY || !PunchyBridge.isPunchyLoaded());
    }

    /** True when Infinium should take over animations of its swords from Punchy. */
    public static boolean overridesPunchy() {
        return ENABLED && OVERRIDE_PUNCHY && PunchyBridge.isPunchyLoaded();
    }
}
