package dev.justin.infinium.client;

import dev.justin.infinium.Infinium;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Out-of-world title backdrop, menu v4 (2026-10-28, rebuilt from scratch): a figure-free cinematic night landscape
 * with a village. Procedural layers textures/gui/menu/v4_*.png (built by /workspace/menu_mock/build_layers_v4.py),
 * 1920x1080 design space, cover-fit; every layer spans x=-60..1980 so the camera can sway.
 * Back to front: sky gradient + baked stars, nebula (breathing), twinkling stars, 18 frozen meteors spread over the
 * whole sky on a jittered grid (glow pulse, trail shimmer, twinkle spikes, orbiting sparks), far mountains, drifting
 * fog, mid hills, fog, village hill + lit windows (flicker), fireflies, foreground tree framing, floating crystal
 * sparkles, vignette and centre darkening for the buttons. Parallax: camera sway amplitude grows with depth.
 * Python mirror (keep in sync): /workspace/menu_mock/mock.py. All motion uses integer frequencies of the 120s loop.
 */
public final class MenuVillage {
    private MenuVillage() {}

    private static final long PERIOD_MS = 120_000L;
    private static final double TAU = Math.PI * 2;
    private static final int HEAD = 0xF0B0FF, CORE = 0xFFE0F6, TRAIL = 0xB050E0, PINK = 0xFF7AD9;

    private record Layer(Identifier tex, float y, float h, int tw, int th, float amp) {}
    private static Layer L(String n, float y, float h, int tw, int th, float amp) {
        return new Layer(Infinium.id("textures/gui/menu/v4_" + n + ".png"), y, h, tw, th, amp);
    }
    private static final Layer SKY = L("sky", 0, 1080, 1020, 540, 4);
    private static final Layer NEBULA = L("nebula", 0, 1080, 510, 270, 4);
    private static final Layer FAR = L("far", 560, 520, 1020, 260, 8);
    private static final Layer FOG = L("fog", 0, 120, 510, 60, 0);
    private static final Layer MID = L("mid", 700, 380, 1020, 190, 12);
    private static final Layer VILLAGE = L("village", 800, 280, 1020, 140, 18);
    private static final Layer WINDOWS = L("windows", 800, 280, 1020, 140, 18);
    private static final Layer FG = L("fg", 760, 320, 1020, 160, 28);

    private static final int N_STARS = 90;
    private static final float[] SU = new float[N_STARS], SV = new float[N_STARS], SB = new float[N_STARS], SP = new float[N_STARS];
    private static final int[] SM = new int[N_STARS];
    private static final double[][] METEORS = new double[18][];

    static {
        java.util.Random r = new java.util.Random(0x51A7L);
        for (int i = 0; i < N_STARS; i++) {
            SU[i] = r.nextFloat(); SV[i] = r.nextFloat() * 0.6F;
            SB[i] = 0.3F + 0.7F * r.nextFloat(); SP[i] = (float) (r.nextFloat() * TAU);
            SM[i] = 3 + r.nextInt(9);
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 6; col++) {
            int i = row * 6 + col;
            METEORS[i] = new double[]{100 + (col + 0.15 + 0.7 * hash(i, 5, 1)) * 1720 / 6.0, 70 + (row + 0.15 + 0.7 * hash(i, 5, 2)) * 490 / 3.0,
                0.45 + 0.4 * hash(i, 5, 3), 26 + 6 * hash(i, 5, 4)};
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

    private static double camX(double a, double t) { return a * Math.sin(TAU * t) + a * 0.4 * Math.sin(TAU * 3 * t + 0.7); }
    private static double camY(double a, double t) { return a * 0.35 * Math.sin(TAU * 2 * t + 1.0) + a * 0.15 * Math.sin(TAU * 5 * t + 2.1); }

    public static void draw(GuiGraphicsExtractor g, int w, int h) {
        draw(g, w, h, (System.currentTimeMillis() % PERIOD_MS) / (double) PERIOD_MS);
    }

    public static void draw(GuiGraphicsExtractor g, int w, int h, double t) {
        g.fill(0, 0, w, h, 0xFF000000);
        float s = Math.max(w / 1920F, h / 1080F) * 1.02F;
        float ox = (w - 1920 * s) / 2F, oy = (h - 1080 * s) / 2F;
        double sdx = camX(4, t), sdy = camY(4, t);

        layer(g, SKY, s, ox, oy, t, 1.0, SKY.y(), 0);
        layer(g, NEBULA, s, ox, oy, t, 0.55 + 0.25 * Math.sin(TAU * 2 * t), NEBULA.y(), 0);
        for (int i = 0; i < N_STARS; i++) {
            double a = 0.8 * SB[i] * (0.55 + 0.45 * Math.sin(TAU * SM[i] * t + SP[i]));
            int x = (int) (ox + (SU[i] * 1920 + sdx) * s), y = (int) (oy + (SV[i] * 1080 + sdy) * s);
            g.fill(x, y, x + 1, y + 1, argb(a, 0xFFEBFF));
        }

        for (int i = 0; i < METEORS.length; i++) {
            double[] m = METEORS[i];
            double sz = m[2], p = hash(i, 0, 7) * TAU, ca = Math.cos(Math.toRadians(m[3])), sa = Math.sin(Math.toRadians(m[3]));
            double pulse = 0.5 + 0.5 * Math.sin(TAU * 6 * t + p), tw = 0.5 + 0.5 * Math.sin(TAU * 15 * t + p * 1.7);
            double cx = ox + (m[0] + sdx) * s, cy = oy + (m[1] + sdy) * s;
            int icx = (int) cx, icy = (int) cy;
            for (int j = 0; j < 18; j++) {
                double f = j / 18.0, len = 150 * sz;
                int x = (int) (cx - ca * f * len * s), y = (int) (cy - sa * f * len * s);
                int ww = Math.max(1, (int) Math.round(4 * sz * (1 - f) + 1));
                double a = 0.85 * Math.pow(1 - f, 1.3) * (0.65 + 0.35 * Math.sin(TAU * 9 * t - f * 8 + p));
                int x0 = x - Math.floorDiv(ww, 2), y0 = y - Math.floorDiv(ww, 2);
                g.fill(x0, y0, x0 + ww, y0 + ww, argb(a, j > 3 ? TRAIL : PINK));
            }
            int[] hr = {(int) (14 * sz) + 3, (int) (9 * sz) + 2, (int) (5 * sz) + 2};
            double[] ha = {0.06, 0.10, 0.18};
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

        layer(g, FAR, s, ox, oy, t, 1.0, FAR.y(), 0);
        layer(g, FOG, s, ox, oy, t, 0.8, 640, 60 * Math.sin(TAU * t + 0.3));
        layer(g, MID, s, ox, oy, t, 1.0, MID.y(), 0);
        layer(g, FOG, s, ox, oy, t, 0.7, 770, -80 * Math.sin(TAU * t + 1.9));
        layer(g, VILLAGE, s, ox, oy, t, 1.0, VILLAGE.y(), 0);
        layer(g, WINDOWS, s, ox, oy, t, 0.55 + 0.15 * Math.sin(TAU * 7 * t) + 0.08 * Math.sin(TAU * 19 * t + 1.3), WINDOWS.y(), 0);

        double fdx = camX(18, t), fdy = camY(18, t);
        for (int i = 0; i < 26; i++) {
            double u = 40 + hash(i, 1, 21) * 1840, v = 760 + hash(i, 1, 22) * 220, p = hash(i, 1, 26) * TAU;
            int a1 = 1 + (int) (hash(i, 1, 23) * 3), b1 = 1 + (int) (hash(i, 1, 24) * 3), c1 = 3 + (int) (hash(i, 1, 25) * 5);
            double x = u + fdx + 30 * Math.sin(TAU * a1 * t + p), y = v + fdy + 16 * Math.sin(TAU * b1 * t + p * 1.3);
            double gl = 0.25 + 0.75 * Math.pow(Math.max(0, Math.sin(TAU * c1 * t + p)), 2);
            int gx = (int) (ox + x * s), gy = (int) (oy + y * s);
            g.fill(gx - 3, gy - 3, gx + 4, gy + 4, argb(0.18 * gl, TRAIL));
            g.fill(gx - 1, gy - 1, gx + 2, gy + 2, argb(0.35 * gl, (i & 1) == 1 ? PINK : HEAD));
            g.fill(gx, gy, gx + 1, gy + 1, argb(0.95 * gl, CORE));
        }

        layer(g, FG, s, ox, oy, t, 1.0, FG.y(), 0);

        double gdx = camX(28, t), gdy = camY(28, t);
        for (int i = 0; i < 30; i++) {
            int k = 3 + (int) (hash(i, 2, 31) * 5);
            double raw = t * k + hash(i, 2, 32), q = raw % 1.0;
            int c = ((int) raw) % k;
            double x = 60 + hash(i, c, 33) * 1800 + gdx + 12 * Math.sin(TAU * q * 2 + i), y = 1060 - hash(i, c, 34) * 260 + gdy - 300 * q;
            double a = 0.9 * Math.min(1, Math.min(q / 0.2, (1 - q) / 0.3)), tw = 0.5 + 0.5 * Math.sin(TAU * q * 6 + i);
            int gx = (int) (ox + x * s), gy = (int) (oy + y * s), col = i % 3 != 0 ? PINK : HEAD;
            g.fill(gx, gy, gx + 2, gy + 2, argb(a, col));
            if (tw > 0.7) {
                g.fill(gx - 2, gy, gx + 4, gy + 1, argb(a * 0.6, col));
                g.fill(gx, gy - 2, gx + 1, gy + 4, argb(a * 0.6, col));
            }
        }

        int vc = argb(0.07, 0);
        for (int k = 0; k < 8; k++) {
            int e = (int) ((k + 1) * h * 0.03), e2 = (int) ((k + 1) * w * 0.02);
            g.fill(0, 0, w, e, vc); g.fill(0, h - e, w, h, vc);
            g.fill(0, 0, e2, h, vc); g.fill(w - e2, 0, w, h, vc);
        }
        for (int x = 0; x < w; x += 4) {
            double u = (x + 2 - w / 2.0) / (0.20 * w);
            g.fill(x, 0, Math.min(w, x + 4), h, argb(0.22 * Math.exp(-u * u), 0));
        }
    }

    private static void layer(GuiGraphicsExtractor g, Layer l, float s, float ox, float oy, double t, double alpha, double y, double xoff) {
        if (alpha <= 0.002) return;
        float dx = (float) camX(l.amp(), t), dy = (float) camY(l.amp(), t);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(ox + (float) (-60 + dx + xoff) * s, oy + (float) (y + dy) * s);
        pose.scale(2040 * s / l.tw(), l.h() * s / l.th());
        g.blit(RenderPipelines.GUI_TEXTURED, l.tex(), 0, 0, 0F, 0F, l.tw(), l.th(), l.tw(), l.th(), argb(alpha, 0xFFFFFF));
        pose.popMatrix();
    }
}
