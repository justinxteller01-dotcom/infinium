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
 * v2 (2026-10-28): meteors are frozen in mid-air (fixed, farther/smaller; twinkle, glow pulse, trail shimmer,
 * orbiting sparks, glints), fireflies, rising crystal motes, gentle camera sway; sky/galaxy/stars unchanged.
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
    private static final Layer FAR = L("far", 0, 666, 1920, 414, 1920, 414, 5);
    private static final Layer GLOW = L("glow", 1141, 840, 388, 86, 388, 86, 5);
    private static final Layer NEAR = L("near", 0, 506, 1920, 574, 1920, 574, 10);
    private static final Layer CRYSTAL = L("crystal", 0, 939, 1920, 141, 1920, 141, 10);

    private static final int N_STARS = 70;
    private static final float[] SU = new float[N_STARS], SV = new float[N_STARS], SB = new float[N_STARS], SP = new float[N_STARS];
    private static final int[] SM = new int[N_STARS];
    // v2 frozen meteors (Fortnite-style, fixed in mid-air, farther/smaller): design x, y, trail angle deg, dir
    private static final int[][] FROZEN = {{300, 120, 28, 1}, {640, 255, 22, -1}, {1330, 95, 34, -1}, {1585, 300, 20, 1}, {1110, 205, 26, 1}};
    private static final int HEAD = 0xF0B0FF, TRAIL = 0xB050E0, PINK = 0xFF7AD9;

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

        // v2 meteors: frozen in the sky (no travel); only twinkle, glow pulse, trail shimmer, orbiting sparks, glints
        for (int i = 0; i < FROZEN.length; i++) {
            int[] m = FROZEN[i];
            double p = hash(i, 0, 7) * TAU, ang = Math.toRadians(m[2]), ca = Math.cos(ang), sa = Math.sin(ang);
            double pulse = 0.5 + 0.5 * Math.sin(TAU * 6 * t + p), tw = 0.5 + 0.5 * Math.sin(TAU * 17 * t + p * 1.7);
            int gx = (int) (ox + (m[0] + skyDx) * s), gy = (int) (oy + m[1] * s);
            g.fill(gx - 1, gy - 1, gx + 2, gy + 2, argb(0.10 + 0.12 * pulse, TRAIL));
            for (int j = 1; j < 10; j++) {
                double f = j / 10.0, tx = m[0] - m[3] * ca * f * 28, ty = m[1] - sa * f * 28;
                double a = 0.55 * (1 - f) * (0.7 + 0.3 * Math.sin(TAU * 11 * t - f * 6 + p));
                int x = (int) (ox + (tx + skyDx) * s), y = (int) (oy + ty * s);
                g.fill(x, y, x + 1, y + 1, argb(a, TRAIL));
            }
            g.fill(gx, gy, gx + 1, gy + 1, argb(0.80 + 0.20 * tw, HEAD));
            for (int j = 0; j < 2; j++) {
                double oa = TAU * 10 * t + p + j * Math.PI;
                int sx = (int) (ox + (m[0] + skyDx + 6 * Math.cos(oa)) * s), sy = (int) (oy + (m[1] + 3.5 * Math.sin(oa)) * s);
                g.fill(sx, sy, sx + 1, sy + 1, argb(0.45 * (0.5 + 0.5 * Math.sin(TAU * 13 * t + p + j)), PINK));
            }
            double gl = Math.pow(Math.max(0, Math.sin(TAU * 4 * t + p)), 8);
            if (gl > 0.02) for (int d = 1; d <= 2; d++) {
                int c = argb(0.6 * gl / d, HEAD);
                g.fill(gx + d, gy, gx + d + 1, gy + 1, c); g.fill(gx - d, gy, gx - d + 1, gy + 1, c);
                g.fill(gx, gy + d, gx + 1, gy + d + 1, c); g.fill(gx, gy - d, gx + 1, gy - d + 1, c);
            }
        }

        layer(g, FAR, s, ox, oy, t, 1.0);
        layer(g, GLOW, s, ox, oy, t, 0.40 + 0.12 * Math.sin(TAU * 7 * t) + 0.07 * Math.sin(TAU * 19 * t + 1.3));
        // v2 fireflies over the village (mid-field, camera amp 7): gentle bob + soft blink
        double fdx = camX(7, t), fdy = camY(7, t);
        for (int i = 0; i < 18; i++) {
            double u = 60 + hash(i, 1, 21) * 1800, v = 640 + hash(i, 1, 22) * 250, p = hash(i, 1, 26) * TAU;
            int a1 = 1 + (int) (hash(i, 1, 23) * 3), b1 = 1 + (int) (hash(i, 1, 24) * 3), c1 = 4 + (int) (hash(i, 1, 25) * 6);
            double x = u + fdx + 14 * Math.sin(TAU * a1 * t + p), y = v + fdy + 8 * Math.sin(TAU * b1 * t + p * 1.3);
            double gl = Math.pow(Math.max(0, Math.sin(TAU * c1 * t + p)), 2);
            int gx = (int) (ox + x * s), gy = (int) (oy + y * s);
            g.fill(gx - 1, gy - 1, gx + 2, gy + 2, argb(0.12 * gl, TRAIL));
            g.fill(gx, gy, gx + 1, gy + 1, argb(0.15 + 0.65 * gl, (i & 1) == 1 ? PINK : HEAD));
        }
        layer(g, NEAR, s, ox, oy, t, 1.0);
        layer(g, CRYSTAL, s, ox, oy, t, 0.30 + 0.22 * Math.sin(TAU * 4 * t));
        // v2 crystal motes rising from the ground crystals (camera amp 10), integer cycles per loop
        double ndx = camX(10, t), ndy = camY(10, t);
        for (int i = 0; i < 16; i++) {
            int k = 6 + (int) (hash(i, 2, 31) * 7);
            double raw = t * k + hash(i, 2, 32), q = raw % 1.0;
            int c = ((int) raw) % k;
            double x = 80 + hash(i, c, 33) * 1760 + ndx + 5 * Math.sin(TAU * q * 2 + i), y = 1040 - hash(i, c, 34) * 60 + ndy - 110 * q;
            double a = 0.55 * Math.min(1, Math.min(q / 0.2, (1 - q) / 0.3));
            int gx = (int) (ox + x * s), gy = (int) (oy + y * s);
            g.fill(gx, gy, gx + 1, gy + 1, argb(a, i % 3 != 0 ? PINK : HEAD));
        }

        // centre darkening so the button column reads
        for (int x = 0; x < w; x += 4) {
            double u = (x + 2 - w / 2.0) / (0.20 * w);
            g.fill(x, 0, Math.min(w, x + 4), h, argb(0.30 * Math.exp(-u * u), 0));
        }
    }

    /** v1 sway (sky/galaxy unchanged) plus the v2 gentle camera sway on scene layers (amp >= 5). */
    private static double camX(double a, double t) {
        return a * Math.sin(TAU * t) + (a >= 5 ? a * 0.35 * Math.sin(TAU * 3 * t + 0.7) : 0);
    }
    private static double camY(double a, double t) {
        return a * 0.4 * Math.sin(TAU * 2 * t + 1.0) + (a >= 5 ? a * 0.25 * Math.sin(TAU * 5 * t + 2.1) : 0);
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
