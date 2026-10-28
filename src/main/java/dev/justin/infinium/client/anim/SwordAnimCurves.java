package dev.justin.infinium.client.anim;

import static dev.justin.infinium.client.anim.SwordAnimTuning.*;

/** Pure math for the sword animations: no Minecraft types, easy to unit-test. */
public final class SwordAnimCurves {
    private SwordAnimCurves() {}

    public static float clamp01(float t) { return t < 0F ? 0F : (t > 1F ? 1F : t); }
    public static float easeOutQuad(float t) { t = clamp01(t); return 1F - (1F - t) * (1F - t); }
    public static float easeInOutCubic(float t) { t = clamp01(t); return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F; }
    public static float easeOutBack(float t) { t = clamp01(t); float c1 = 1.70158F, c3 = c1 + 1F; return 1F + c3 * (float) Math.pow(t - 1F, 3) + c1 * (float) Math.pow(t - 1F, 2); }

    /** Windup amount (0..1..0): rises during windup, released during strike. */
    public static float windup(float p) {
        if (p <= 0F || p >= 1F) return 0F;
        if (p < WINDUP_END) return easeOutQuad(p / WINDUP_END);
        if (p < STRIKE_END) return 1F - easeInOutCubic((p - WINDUP_END) / (STRIKE_END - WINDUP_END));
        return 0F;
    }

    /**
     * Strike amount: 0 at rest, 1 at end of the arc, then decays back to 0 with a damped
     * overshoot so the blade trails past the target and settles.
     */
    public static float strike(float p) {
        if (p <= 0F || p >= 1F) return 0F;
        if (p < WINDUP_END) return 0F;
        if (p < STRIKE_END) return easeInOutCubic((p - WINDUP_END) / (STRIKE_END - WINDUP_END));
        float r = (p - STRIKE_END) / (1F - STRIKE_END);           // 0..1 recovery
        float settle = 1F - easeInOutCubic(r);                     // back to rest
        float trail = FOLLOW_OVERSHOOT * (float) (Math.exp(-FOLLOW_DAMPING * r) * Math.sin(Math.PI * FOLLOW_FREQ * r));
        return settle + trail * (1F - r);
    }

    /** Equip flourish 0..1 remaining (1 = just drawn, 0 = settled). */
    public static float equip(float ticksSinceEquip) {
        if (ticksSinceEquip >= EQUIP_TICKS || ticksSinceEquip < 0F) return 0F;
        return 1F - easeOutBack(ticksSinceEquip / EQUIP_TICKS);
    }

    public static float breath(float time) { return (float) Math.sin(time * 2.0 * Math.PI / IDLE_BREATH_PERIOD); }
    public static float sway(float time) { return (float) Math.sin(time * 2.0 * Math.PI / IDLE_SWAY_PERIOD); }
}
