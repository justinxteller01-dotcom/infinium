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
 * Layers back to front: sky, galaxy glow (breathing), stars (twinkle), meteors (ShootingStarLook colours, upper sky
 * only, never over the player), far (hills/trees/village), window glow (flicker), near (hill + player), ground
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
    private static final int[] METEOR_K = {8, 9, 10, 11, 12, 10, 9, 8};   // cycles per loop (period 120/k s)

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

        // meteors: sparse (~4-6 visible), design y 40..380 (player's head starts ~500), seamless (integer k per loop)
        for (int i = 0; i < METEOR_K.length; i++) {
            int k = METEOR_K[i];
            double raw = t * k + hash(i, 0, 9);
            double ph = raw % 1.0;
            int c = ((int) raw) % k;
            double D = 0.55;
            if (ph > D) continue;
            double q = ph / D, fade = Math.min(1, Math.min(q / 0.15, (1 - q) / 0.2));
            double sx = 150 + hash(i, c, 1) * 1500, sy = 40 + hash(i, c, 2) * 170;
            double ang = Math.toRadians(18 + hash(i, c, 3) * 22);
            int dir = hash(i, c, 4) < 0.5 ? 1 : -1;
            double len = 420 + hash(i, c, 5) * 200;
            double cs = Math.cos(ang), sn = Math.sin(ang);
            double hx = sx + dir * cs * len * q, hy = sy + sn * len * q;
            for (int j = 0; j < 14; j++) {
                double f = j / 14.0, tx = hx - dir * cs * f * 70, ty = hy - sn * f * 70;
                if (ty > 380) continue;
                int gx = (int) (ox + (tx + skyDx) * s), gy = (int) (oy + ty * s);
                int sz = j < 2 ? 2 : 1;
                int col = j == 0 ? ShootingStarLook.HEAD_RGB : ShootingStarLook.TRAIL_RGB;
                g.fill(gx, gy, gx + sz, gy + sz, argb(fade * (j == 0 ? 1.0 : 0.75 * (1 - f)), col));
            }
        }

        layer(g, FAR, s, ox, oy, t, 1.0);
        layer(g, GLOW, s, ox, oy, t, 0.40 + 0.12 * Math.sin(TAU * 7 * t) + 0.07 * Math.sin(TAU * 19 * t + 1.3));
        layer(g, NEAR, s, ox, oy, t, 1.0);
        layer(g, CRYSTAL, s, ox, oy, t, 0.30 + 0.22 * Math.sin(TAU * 4 * t));

        // centre darkening so the button column reads
        for (int x = 0; x < w; x += 4) {
            double u = (x + 2 - w / 2.0) / (0.20 * w);
            g.fill(x, 0, Math.min(w, x + 4), h, argb(0.30 * Math.exp(-u * u), 0));
        }
    }

    private static void layer(GuiGraphicsExtractor g, Layer l, float s, float ox, float oy, double t, double alpha) {
        if (alpha <= 0.002) return;
        float dx = (float) (l.amp() * Math.sin(TAU * t)), dy = (float) (l.amp() * 0.4 * Math.sin(TAU * 2 * t + 1.0));
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(ox + (l.x() + dx) * s, oy + (l.y() + dy) * s);
        pose.scale(l.w() * s / l.tw(), l.h() * s / l.th());
        g.blit(RenderPipelines.GUI_TEXTURED, l.tex(), 0, 0, 0F, 0F, l.tw(), l.th(), l.tw(), l.th(), argb(alpha, 0xFFFFFF));
        pose.popMatrix();
    }
}
