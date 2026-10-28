package dev.justin.infinium.client;

import dev.justin.infinium.Infinium;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Out-of-world title backdrop: "village hill" painting (from Justin's gemini_village reference, AI shards removed),
 * split into layers under textures/gui/menu/village_*.png, laid out in a 1920x1080 design space and cover-fit.
 * Python mirror (keep in sync): /workspace/menu_mock/mock.py. Everything is a function of the 120s loop phase
 * with integer frequencies, so frame(T) == frame(0).
 * v3 (2026-10-28): large bright frozen meteors (cluster + strays; glow pulse, trail shimmer, twinkle spikes, orbiting
 * sparks), fireflies, floating crystal sparkles, high camera (hill pushed down, ground crystals removed) with a
 * noticeable gentle sway; sky/galaxy/stars unchanged.
 * Layers back to front: sky, galaxy glow (breathing), stars (twinkle), frozen meteors (upper sky only), far (hills/trees/village), window glow (flicker), near (hill + player), ground
 * crystal glow (pulse), centre darkening for the buttons. Parallax sway: sky 3, far 5, near 10 design px.
 */
public final class MenuVillage {
    private MenuVillage() {}

    private static final long PERIOD_MS = 120_000L;
    private static final double TAU = Math.PI * 2;

    // name, design x, y, w, h, texture w, h, sway amplitude (design px)
    private record Layer(Identifier tex, float x, float y, float w, float h, int tw, int th, float amp) {}
    private static Layer L(String n, float x, float y, float w, float h, int tw, int th, float amp) {
        return new Layer(Infinium.id("textures/gui/menu/village_" + n + ".png"), x, y, w, h, tw, th, amp);
    }
    private static final Layer SKY = L("sky", 0, 0, 1920, 1080, 1280, 720, 3);
    private static final Layer GALAXY = L("galaxy", 0, 0, 1920, 1080, 960, 540, 3);
    // v3 high camera: hill/ground pushed down (ground crystals off-screen, crystal glow layer no longer drawn)
    private static final Layer FAR = L("far", 0, 866, 1920, 414, 1920, 414, 14);
    private static final Layer GLOW = L("glow", 1141, 1040, 388, 86, 388, 86, 14);
    private static final Layer NEAR = L("near", 0, 836, 1920, 574, 1920, 574, 20);

    private static final int N_STARS = 70;
    private static final float[] SU = new float[N_STARS], SV = new float[N_STARS], SB = new float[N_STARS], SP = new float[N_STARS];
    private static final int[] SM = new int[N_STARS];
    // v3 frozen meteors: x, y, size, trail angle deg, dir. Tight cluster around (1340,230) + strays
    private static final double[][] METEORS = {{1340, 230, 1.0, 30, 1}, {1395, 262, 0.8, 30, 1}, {1300, 282, 0.7, 30, 1}, {1420, 205, 0.6, 30, 1},
        {1362, 318, 0.55, 30, 1}, {1268, 214, 0.5, 30, 1}, {1450, 300, 0.45, 30, 1},
        {380, 150, 0.8, 24, -1}, {700, 300, 0.55, 26, -1}, {1700, 110, 0.6, 34, 1}, {980, 95, 0.45, 28, 1}};
    private static final int HEAD = 0xF0B0FF, CORE = 0xFFE0F6, TRAIL = 0xB050E0, PINK = 0xFF7AD9, HAZE = 0x0B0614;

    static {
        java.util.Random r = new java.util.Random(0x51A7L);
        for (int i = 0; i < N_STARS; i++) {
            SU[i] = r.nextFloat(); SV[i] = r.nextFloat() * 0.55F;
            SB[i] = 0.3F + 0.7F * r.nextFloat(); SP[i] = (float) (r.nextFloat() * TAU);
            SM[i] = 3 + r.nextInt(9);
        }
    }

    private static double hash(int i, int c, int s) {
        long x = ((i * 73856093L) ^ (c * 19349663L) ^ (s * 83492791L)) & 0xffffffffL;
        x = ((x ^ (x >>> 13)) * 1274126177L) & 0xffffffffL;
        return ((x ^ (x >>> 16)) & 0xffffL) / 65536.0;
    }

    private static int argb(double a, int rgb) {
        int ai = (int) Math.round(Math.max(0, Math.min(1, a)) * 255);
        return (ai << 24) | (rgb & 0xFFFFFF);
    }

    public static void draw(GuiGraphicsExtractor g, int w, int h) {
        double t = (System.currentTimeMillis() % PERIOD_MS) / (double) PERIOD_MS;
        draw(g, w, h, t);
    }

    public static void draw(GuiGraphicsExtractor g, int w, int h, double t) {
        g.fill(0, 0, w, h, 0xFF000000);
        float s = Math.max(w / 1920F, h / 1080F) * 1.02F;
        float ox = (w - 1920 * s) / 2F, oy = (h - 1080 * s) / 2F;
        double skyDx = 3 * Math.sin(TAU * t);

        layer(g, SKY, s, ox, oy, t, 1.0);
        layer(g, GALAXY, s, ox, oy, t, 0.45 + 0.25 * Math.sin(TAU * 2 * t));

        for (int i = 0; i < N_STARS; i++) {
            double a = 0.75 * SB[i] * (0.55 + 0.45 * Math.sin(TAU * SM[i] * t + SP[i]));
            int x = (int) (ox + (SU[i] * 1920 + skyDx) * s), y = (int) (oy + SV[i] * 1080 * s);
            g.fill(x, y, x + 1, y + 1, argb(a, 0xFFEBFF));
        }

        // v3 frozen meteors (fixed in the sky, sky sway only): glow pulse, trail shimmer, twinkle spikes, orbiting sparks
        for (int i = 0; i < METEORS.length; i++) {
            double[] m = METEORS[i];
            double sz = m[2], dr = m[4], p = hash(i, 0, 7) * TAU, ca = Math.cos(Math.toRadians(m[3])), sa = Math.sin(Math.toRadians(m[3]));
            double pulse = 0.5 + 0.5 * Math.sin(TAU * 6 * t + p), tw = 0.5 + 0.5 * Math.sin(TAU * 15 * t + p * 1.7);
            double cx = ox + (m[0] + skyDx) * s, cy = oy + m[1] * s;
            int icx = (int) cx, icy = (int) cy;
            for (int j = 0; j < 18; j++) {
                double f = j / 18.0, L = 150 * sz;
                int x = (int) (cx - dr * ca * f * L * s), y = (int) (cy - sa * f * L * s);
                int w_ = Math.max(1, (int) Math.round(4 * sz * (1 - f) + 1));
                double a = 0.85 * Math.pow(1 - f, 1.3) * (0.65 + 0.35 * Math.sin(TAU * 9 * t - f * 8 + p));
                int x0 = x - Math.floorDiv(w_, 2), y0 = y - Math.floorDiv(w_, 2);
                g.fill(x0, y0, x0 + w_, y0 + w_, argb(a, j > 3 ? TRAIL : PINK));
            }
            int[] hr = {(int) (14 * sz) + 3, (int) (9 * sz) + 2, (int) (5 * sz) + 2};
            double[] ha = {0.10, 0.16, 0.26};
            for (int k = 0; k < 3; k++) g.fill(icx - hr[k], icy - hr[k], icx + hr[k] + 1, icy + hr[k] + 1, argb(ha[k] * (0.5 + pulse), TRAIL));
            int r = (int) (2 * sz) + 1;
            g.fill(icx - r, icy - r, icx + r + 1, icy + r + 1, argb(0.95, HEAD));
            g.fill(icx - r + 1, icy - r + 1, icx + r, icy + r, argb(0.7 + 0.3 * tw, CORE));
            int sp = (int) ((6 + 10 * tw) * sz) + 2;
            g.fill(icx - sp, icy, icx + sp + 1, icy + 1, argb(0.35 + 0.45 * tw, CORE));
            g.fill(icx, icy - sp, icx + 1, icy + sp + 1, argb(0.35 + 0.45 * tw, CORE));
            for (int j = 0; j < 3; j++) {
                double oa = TAU * (8 + j) * t + p + j * TAU / 3, R = (22 + 6 * j) * sz;
                int x = (int) (cx + R * Math.cos(oa) * s), y = (int) (cy + R * 0.55 * Math.sin(oa) * s);
                g.fill(x, y, x + 2, y + 2, argb(0.4 + 0.5 * (0.5 + 0.5 * Math.sin(TAU * 13 * t + p + j)), PINK));
            }
        }

        // patch over the hidden player's baked ghost in the sky painting (sky sway)
        int[] br = {120, 90, 62, 36};
        double[] ba = {0.30, 0.35, 0.40, 0.45};
        for (int k = 0; k < 4; k++) {
            double bx = ox + (935 + skyDx) * s, by = oy + 590 * s;
            g.fill((int) (bx - br[k] * s), (int) (by - br[k] * 1.4 * s), (int) (bx + br[k] * s), (int) (by + br[k] * 1.4 * s), argb(ba[k], 0x161538));
        }
        // high camera: purple-black haze over the lower sky / valley
        double hy = camY(14, t);
        for (int y = (int) (oy + (560 + hy) * s); y < h; y += 2) {
            double d = (y - oy) / s - 560 - hy;
            g.fill(0, y, w, y + 2, argb(Math.min(1, d / 200) * 0.92, HAZE));
        }

        layer(g, FAR, s, ox, oy, t, 1.0);
        layer(g, GLOW, s, ox, oy, t, 0.40 + 0.12 * Math.sin(TAU * 7 * t) + 0.07 * Math.sin(TAU * 19 * t + 1.3));
        layer(g, NEAR, s, ox, oy, t, 1.0);

        // fireflies (drift + blink) and floating crystal sparkles (rise + twinkle), scene sway
        double fdx = camX(14, t), fdy = camY(14, t);
        for (int i = 0; i < 28; i++) {
            double u = 40 + hash(i, 1, 21) * 1840, v = 560 + hash(i, 1, 22) * 440, p = hash(i, 1, 26) * TAU;
            int a1 = 1 + (int) (hash(i, 1, 23) * 3), b1 = 1 + (int) (hash(i, 1, 24) * 3), c1 = 3 + (int) (hash(i, 1, 25) * 5);
            double x = u + fdx + 30 * Math.sin(TAU * a1 * t + p), y = v + fdy + 16 * Math.sin(TAU * b1 * t + p * 1.3);
            double gl = 0.25 + 0.75 * Math.pow(Math.max(0, Math.sin(TAU * c1 * t + p)), 2);
            int gx = (int) (ox + x * s), gy = (int) (oy + y * s);
            g.fill(gx - 3, gy - 3, gx + 4, gy + 4, argb(0.18 * gl, TRAIL));
            g.fill(gx - 1, gy - 1, gx + 2, gy + 2, argb(0.35 * gl, (i & 1) == 1 ? PINK : HEAD));
            g.fill(gx, gy, gx + 1, gy + 1, argb(0.95 * gl, CORE));
        }
        for (int i = 0; i < 30; i++) {
            int k = 3 + (int) (hash(i, 2, 31) * 5);
            double raw = t * k + hash(i, 2, 32), q = raw % 1.0;
            int c = ((int) raw) % k;
            double x = 60 + hash(i, c, 33) * 1800 + fdx + 12 * Math.sin(TAU * q * 2 + i), y = 1000 - hash(i, c, 34) * 200 + fdy - 260 * q;
            double a = 0.9 * Math.min(1, Math.min(q / 0.2, (1 - q) / 0.3)), tw = 0.5 + 0.5 * Math.sin(TAU * q * 6 + i);
            int gx = (int) (ox + x * s), gy = (int) (oy + y * s), col = i % 3 != 0 ? PINK : HEAD;
            g.fill(gx, gy, gx + 2, gy + 2, argb(a, col));
            if (tw > 0.7) {
                g.fill(gx - 2, gy, gx + 4, gy + 1, argb(a * 0.6, col));
                g.fill(gx, gy - 2, gx + 1, gy + 4, argb(a * 0.6, col));
            }
        }

        // centre darkening so the button column reads
        for (int x = 0; x < w; x += 4) {
            double u = (x + 2 - w / 2.0) / (0.20 * w);
            g.fill(x, 0, Math.min(w, x + 4), h, argb(0.30 * Math.exp(-u * u), 0));
        }
    }

    /** v1 sway (sky/galaxy unchanged) plus the v2 gentle camera sway on scene layers (amp >= 5). */
    private static double camX(double a, double t) {
        return a * Math.sin(TAU * t) + (a >= 5 ? a * 0.4 * Math.sin(TAU * 3 * t + 0.7) : 0);
    }
    private static double camY(double a, double t) {
        return a * 0.4 * Math.sin(TAU * 2 * t + 1.0) + (a >= 5 ? a * 0.3 * Math.sin(TAU * 5 * t + 2.1) : 0);
    }

    private static void layer(GuiGraphicsExtractor g, Layer l, float s, float ox, float oy, double t, double alpha) {
        if (alpha <= 0.002) return;
        float dx = (float) camX(l.amp(), t), dy = (float) camY(l.amp(), t);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(ox + (l.x() + dx) * s, oy + (l.y() + dy) * s);
        pose.scale(l.w() * s / l.tw(), l.h() * s / l.th());
        g.blit(RenderPipelines.GUI_TEXTURED, l.tex(), 0, 0, 0F, 0F, l.tw(), l.th(), l.tw(), l.th(), argb(alpha, 0xFFFFFF));
        pose.popMatrix();
    }
}
