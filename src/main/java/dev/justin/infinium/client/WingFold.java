package dev.justin.infinium.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Wing absorb / emerge transition per player (render-state id), wall-clock based. t = 0 absorbed (not drawn), 1 fully out.
 * Folding / rolling poses were removed: OFF = wings shrink and retract into the chestplate back, then disappear.
 */
public final class WingFold {
    public static final float DURATION_MS = 450F;
    public static final float FLIGHT_DURATION_MS = 320F;
    public static final float REST_SCALE = 1.0F;                       // revert to original big wings
    public static final float GROUND_RX = 0.24F, GROUND_RZ = -0.28F;   // pointing down towards floor when on ground
    public static final float FLIGHT_RX = 0.35F, FLIGHT_RZ = -0.95F;   // spread out wide when flying
    public static final float OPEN_RX = GROUND_RX, OPEN_RZ = GROUND_RZ;
    private static final Map<Integer, float[]> STATE = new HashMap<>(); // id -> {foldT, lastFoldMs, flightT, lastFlightMs}
    private static long lastSweep;

    public static float progress(int id, boolean open) {
        long now = System.currentTimeMillis();
        float target = open ? 1F : 0F;
        float[] s = STATE.get(id);
        if (s == null) { STATE.put(id, new float[]{target, now, 0F, now}); sweep(now); return target; }
        float dt = Math.min(250F, Math.max(0F, now - s[1]));
        s[1] = now;
        float step = dt / DURATION_MS;
        s[0] = s[0] < target ? Math.min(target, s[0] + step) : Math.max(target, s[0] - step);
        return s[0];
    }

    /** 0 = resting compact size on backplate, 1 = full elytra wingspan while actively fall-flying. */
    public static float flightProgress(int id, boolean flying) {
        long now = System.currentTimeMillis();
        float target = flying ? 1F : 0F;
        float[] s = STATE.get(id);
        if (s == null) { STATE.put(id, new float[]{flying ? 1F : 0F, now, target, now}); sweep(now); return target; }
        float dt = Math.min(250F, Math.max(0F, now - s[3]));
        s[3] = now;
        float step = dt / FLIGHT_DURATION_MS;
        s[2] = s[2] < target ? Math.min(target, s[2] + step) : Math.max(target, s[2] - step);
        return s[2];
    }

    public static int ACTIVE_ID = -1;
    /** True while the wings are still coming out or going in, so the layer keeps drawing after GLIDER is removed. */
    public static boolean forceDraw() {
        float[] s = STATE.get(ACTIVE_ID);
        return s != null && s[0] > 0.001F;
    }
    /** Reset state for a player when wings are toggled off. */
    public static void reset(int id) {
        STATE.remove(id);
    }
    /** Last known progress without advancing (particles). */
    public static float peek(int id) { float[] s = STATE.get(id); return s == null ? -1F : s[0]; }
    private static void sweep(long now) {
        if (now - lastSweep < 10_000L) return;
        lastSweep = now;
        STATE.values().removeIf(s -> now - s[1] > 30_000L);
    }
    public static float smooth(float t) { t = Math.max(0F, Math.min(1F, t)); return t * t * (3F - 2F * t); }
    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private WingFold() {}
}
