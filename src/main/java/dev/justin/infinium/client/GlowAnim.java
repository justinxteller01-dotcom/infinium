package dev.justin.infinium.client;

import java.io.InputStream;
import java.util.Properties;

/** One clock for every Infinium glow (worn armor, wings, helmet extras): frames + frameMs from
 *  /assets/infinium/glow_anim.properties (written by the texture generator), default 48 x 100 ms. */
public final class GlowAnim {
    public static final int FRAMES, FRAME_MS;
    static {
        int fr = 48, ms = 100;
        try (InputStream in = GlowAnim.class.getResourceAsStream("/assets/infinium/glow_anim.properties")) {
            if (in != null) { Properties p = new Properties(); p.load(in);
                fr = Integer.parseInt(p.getProperty("frames", "48").trim()); ms = Integer.parseInt(p.getProperty("frameMs", "100").trim()); }
        } catch (Exception ignored) {}
        FRAMES = Math.max(1, fr); FRAME_MS = Math.max(1, ms);
    }
    public static long loopMs() { return (long) FRAMES * FRAME_MS; }
    /** Frame index of an n-frame sequence at the shared clock (same loop length, same phase). */
    public static int frame(int n) { return (int) ((System.currentTimeMillis() % loopMs()) * n / loopMs()); }
    /** Frame index of an n-frame loop of its own length (n x frameMs), e.g. per-style glow textures. */
    public static int frame(int n, int frameMs) { return frame(n, frameMs, 1.0F); }
    /** Frame index scaled by speedMul (0.55x at unupgraded Tier 1 up to 1.75x at fully upgraded Infinity). */
    public static int frame(int n, int frameMs, float speedMul) {
        int effMs = Math.max(15, Math.round(frameMs / Math.max(0.25F, speedMul)));
        long loop = (long) n * effMs;
        return (int) ((System.currentTimeMillis() % loop) / effMs);
    }
    /** Linear texture animation speed across the entire armor spectrum (0.0 .. 1.0). */
    public static float speedMul(float visualProgress) {
        float p = Math.max(0F, Math.min(1F, visualProgress));
        return 0.55F + 1.20F * p;
    }
    private GlowAnim() {}
}
