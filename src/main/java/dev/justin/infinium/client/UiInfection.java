package dev.justin.infinium.client;

import dev.justin.infinium.SetLook;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * pass2: UI Effects (gated by InfiniumClientConfig.uiEffects at the call sites).
 * - menuBackdrop: out-of-world screens only (level == null). Deep-space void from the reference image: black, a faint purple
 *   galactic band across the middle, twinkling stars, sparse purple crystal shards drifting in random (fixed) directions,
 *   slow camera drift with parallax. Every motion is periodic with the same loop length, so it loops seamlessly.
 * - guideBackdrop: the same scene for the Informational Crystal guide, darker, fewer stars/shards, slower, less camera drift.
 * - panelFx: player inventory effects drawn inside the panel only (before items), intensity follows the worn set's
 *   crystal energy (CrystalCharge.visualProgress 0..1): calm motes at 0, cracks / edge energy / shimmer as it climbs.
 */
public final class UiInfection {

    // ---------------------------------------------------------------- cosmos (menu + guide)
    private record Profile(long periodMs, int stars, int starAlpha, int bandAlpha, int shards, int maxK, int shardAlpha, float camAmp) {}
    private static final Profile MENU = new Profile(120_000L, 120, 190, 10, 8, 2, 220, 6F);
    private static final Profile GUIDE = new Profile(240_000L, 50, 105, 6, 4, 1, 150, 1.5F);

    private static final int MAX_STARS = 140, MAX_SHARDS = 10;
    private static final float[] SX = new float[MAX_STARS], SY = new float[MAX_STARS], SB = new float[MAX_STARS], SP = new float[MAX_STARS];
    private static final int[] SM = new int[MAX_STARS];
    private static final float[] HU = new float[MAX_SHARDS], HV = new float[MAX_SHARDS], HP = new float[MAX_SHARDS];
    private static final int[] HKX = new int[MAX_SHARDS], HKY = new int[MAX_SHARDS], HM = new int[MAX_SHARDS], HC = new int[MAX_SHARDS];
    private static final int[] SHARD_COLS = {0xD864FF, 0xB04CFF, 0xFF66E0, 0x9B3BFF};
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}, {2, 1}, {-2, 1}, {2, -1}, {-2, -1}, {1, 2}, {-1, -2}, {0, 1}, {0, -1}};
    private static final float BAND_SLOPE = -0.16F;

    static {
        java.util.Random r = new java.util.Random(0x1F1A1L);   // fixed seed: the scene never jumps
        for (int i = 0; i < MAX_STARS; i++) {
            SX[i] = r.nextFloat();
            // a third of the stars cluster along the galactic band
            SY[i] = (i % 3 == 0) ? 0.52F + BAND_SLOPE * (SX[i] - 0.5F) + (r.nextFloat() - 0.5F) * 0.22F : r.nextFloat();
            SB[i] = 0.25F + r.nextFloat() * 0.75F;
            SP[i] = r.nextFloat() * 6.2832F;
            SM[i] = 3 + r.nextInt(10);   // integer twinkles per loop -> seamless
        }
        for (int i = 0; i < MAX_SHARDS; i++) {
            HU[i] = r.nextFloat(); HV[i] = r.nextFloat(); HP[i] = r.nextFloat() * 6.2832F;
            int[] d = DIRS[r.nextInt(DIRS.length)];
            HKX[i] = d[0]; HKY[i] = d[1];
            HM[i] = 2 + r.nextInt(5);
            HC[i] = SHARD_COLS[i % SHARD_COLS.length];
        }
    }

    // clip box for the current cosmos call (guide draws in book space and must stay inside the page)
    private static int cx0, cy0, cx1, cy1;

    private static void rect(GuiGraphicsExtractor g, int ax, int ay, int bx, int by, int argb) {
        if (ax < cx0) ax = cx0;
        if (ay < cy0) ay = cy0;
        if (bx > cx1) bx = cx1;
        if (by > cy1) by = cy1;
        if (bx > ax && by > ay) g.fill(ax, ay, bx, by, argb);
    }

    /** Out-of-world menu background. Only called when uiEffects is on (vanilla otherwise). */
    public static void menuBackdrop(GuiGraphicsExtractor g, int w, int h) {
        g.fill(0, 0, w, h, 0xFF000000);
        cosmos(g, 0, 0, w, h, MENU);
    }

    /** Guide page background (book space). Caller fills the page black first. */
    public static void guideBackdrop(GuiGraphicsExtractor g, int x0, int y0, int w, int h) {
        cosmos(g, x0, y0, w, h, GUIDE);
    }

    private static void cosmos(GuiGraphicsExtractor g, int x0, int y0, int w, int h, Profile pf) {
        cx0 = x0; cy0 = y0; cx1 = x0 + w; cy1 = y0 + h;
        double t = (System.currentTimeMillis() % pf.periodMs()) / (double) pf.periodMs();   // 0..1, exact loop
        double TAU = Math.PI * 2;
        float camX = (float) (pf.camAmp() * Math.sin(TAU * t));
        float camY = (float) (pf.camAmp() * 0.6 * Math.sin(TAU * 2 * t + 1.3));

        // galactic band: stacked translucent columns, gently breathing
        int step = 3;
        double breathe = 0.85 + 0.15 * Math.sin(TAU * t);
        for (int x = x0; x < x0 + w; x += step) {
            double u = (x - x0) / (double) w;
            double cy = y0 + h * 0.52 + BAND_SLOPE * (x - x0 - w / 2.0) + camY * 0.5
                    + h * 0.012 * Math.sin(TAU * (u * 1.5 + t));
            double thick = h * 0.15 * (0.80 + 0.20 * Math.sin(u * 7.0 + TAU * t) * Math.cos(u * 3.0));
            for (int k = 0; k < 4; k++) {
                double hh = thick * (1.0 - k * 0.22);
                int a = (int) (pf.bandAlpha() * breathe * (k == 3 ? 1.4 : 1.0));
                int col = k == 3 ? 0x8A3AB0 : 0x4A1A78;
                int xx = x + Math.round(camX * 0.5F);
                rect(g, xx, (int) (cy - hh / 2), xx + step, (int) (cy + hh / 2), (a << 24) | col);
            }
        }

        // stars: fixed positions, integer-frequency twinkle
        for (int i = 0; i < pf.stars(); i++) {
            float tw = 0.70F + 0.30F * (float) Math.sin(TAU * SM[i] * t + SP[i]);
            int a = (int) (pf.starAlpha() * SB[i] * tw);
            if (a < 6) continue;
            int x = x0 + (int) (SX[i] * w + camX * 0.35F), y = y0 + (int) (SY[i] * h + camY * 0.35F);
            int sz = SB[i] > 0.9F ? 2 : 1;
            int c = (i % 7 == 0) ? 0xE8D8FF : 0xFFFFFF;
            rect(g, x, y, x + sz, y + sz, (a << 24) | c);
        }

        // shards: edge-to-edge linear traversal. Each shard spawns outside one edge, travels across the entire screen,
        // and only despawns once it has completely cleared the opposite screen boundary.
        int margin = 48;
        long now = System.currentTimeMillis();
        final int[][] PATHS = {
            {0, 25, 1, 45, 42000, 0},       // Left to Right (slight down-angle)
            {2, 85, 0, 75, 48000, 17000},   // Top-Right to Bottom-Left
            {0, 80, 2, 90, 38000, 8500},    // Bottom-Left to Top-Right
            {1, 60, 0, 35, 52000, 29000},   // Right to Left (slight up-angle)
            {2, 15, 1, 85, 45000, 12000},   // Top-Left to Bottom-Right
            {3, 40, 2, 55, 50000, 35000},   // Bottom to Top (near-vertical)
            {0, 70, 1, 50, 39000, 22000},   // Left to Right (lower half)
            {1, 20, 0, 30, 46000, 41000},   // Right to Left (upper half)
            {2, 65, 0, 90, 54000, 6000},    // Top to Bottom-Left
            {1, 85, 2, 30, 41000, 19000}    // Bottom-Right to Top-Left
        };

        for (int i = 0; i < pf.shards(); i++) {
            int[] path = PATHS[i % PATHS.length];
            long dur = path[4];
            long off = path[5];
            double u = ((now + off) % dur) / (double) dur;

            double sx = path[0] == 0 ? -margin : path[0] == 1 ? w + margin : (path[1] / 100.0) * w;
            double sy = path[0] == 2 ? -margin : path[0] == 3 ? h + margin : (path[1] / 100.0) * h;

            double ex = path[2] == 0 ? -margin : path[2] == 1 ? w + margin : (path[3] / 100.0) * w;
            double ey = path[2] == 2 ? -margin : path[2] == 3 ? h + margin : (path[3] / 100.0) * h;

            double px = sx + u * (ex - sx) + x0 + camX;
            double py = sy + u * (ey - sy) + y0 + camY;
            double vx = ex - sx, vy = ey - sy, vl = Math.max(1e-6, Math.hypot(vx, vy));
            double dx = vx / vl, dy = vy / vl;
            double pulse = 0.80 + 0.20 * Math.sin(TAU * HM[i] * (now % 60000L / 60000.0) + HP[i]);
            int col = HC[i];
            int len = 14;
            for (int s = len; s >= 1; s--) {   // trail, fading away from the head
                double f = 1.0 - s / (double) len;
                int a = (int) (pf.shardAlpha() * 0.55 * f * f * pulse);
                if (a < 4) continue;
                int tx = (int) Math.round(px - dx * s), ty = (int) Math.round(py - dy * s);
                rect(g, tx, ty, tx + 1, ty + 1, (a << 24) | col);
            }
            int a = (int) (pf.shardAlpha() * pulse);
            int hx = (int) Math.round(px), hy = (int) Math.round(py);
            rect(g, hx - 1, hy, hx + 3, hy + 2, ((a / 3) << 24) | col);       // soft halo
            rect(g, hx, hy - 1, hx + 2, hy + 3, ((a / 3) << 24) | col);
            rect(g, hx, hy, hx + 2, hy + 2, (a << 24) | col);                  // crystal core
            rect(g, hx, hy, hx + 1, hy + 1, ((a * 3 / 4) << 24) | 0xFFD8FF);   // glint
        }
    }

    private static double mod(double a, double m) {
        double r = a % m;
        return r < 0 ? r + m : r;
    }

    // ---------------------------------------------------------------- inventory panel effects
    private static int[] palette(SetLook look) {
        if (look == null) return new int[]{0xC45AD4, 0x8A3AA0, 0xE070FF};
        return switch (look) {
            case ASTRAL -> new int[]{0xC45AD4, 0x8A3AA0, 0xE070FF};
            case CYBERNETIC -> new int[]{0xD24A8A, 0xFF66C4, 0xB03A70};
            case SINGULARITY -> new int[]{0xA14AD0, 0x7A3AE0, 0xD8A0FF};
            case CYBERPSYCHO -> new int[]{0xE0235A, 0xFF4CE0, 0xB04CFF};
            case INFINITY -> new int[]{0xD84CFF, 0xFF66E0, 0x9B3BFF};
        };
    }

    /**
     * Inside the container panel only, panel-relative coordinates (0..w, 0..h), drawn before the items.
     * @param p crystal energy 0..1 across the whole armor journey (0 = no / unupgraded tier 1, 1 = fully upgraded Infinity)
     */
    public static void panelFx(GuiGraphicsExtractor g, int w, int h, float p, SetLook look) {
        if (look == SetLook.INFINITY) p = Math.max(p, 0.75F);
        p = Math.max(0F, Math.min(1F, p));
        cx0 = 3; cy0 = 3; cx1 = w - 3; cy1 = h - 3;
        long ms = System.currentTimeMillis();
        int[] pal = palette(look);

        // 1. drifting motes: few and slow when calm, more and quicker as energy rises
        int motes = 4 + Math.round(p * 16);
        for (int i = 0; i < motes; i++) {
            int hh = hash(i, 911);
            int period = (int) ((8000 + (hh & 4095)) / (1F + 1.2F * p));
            float t = ((ms + ((hh >>> 7) & 16383)) % period) / (float) period;
            int x = 4 + Math.floorMod(hh >>> 3, Math.max(1, w - 8)) + (int) (Math.sin(ms / 1300.0 + i) * 3);
            int y = h - 4 - (int) (t * (h - 8));
            int a = (int) ((70 + 140 * p) * Math.sin(Math.PI * t));
            if (a < 6) continue;
            int sz = (p > 0.3F && (i & 1) == 0) ? 2 : 1;
            rect(g, x, y, x + sz, y + sz, (a << 24) | pal[i % 3]);
        }

        // 2. random hairline crack pulses (more frequent and brighter with energy)
        int cracks = 1 + (int) (p * 3.5F);
        long cycle = (long) (5000 - 2800 * p);
        for (int c = 0; c < cracks; c++) {
            long tt = ms + c * 2731L;
            long idx = tt / cycle;
            float lt = (tt % cycle) / (float) cycle;
            if (lt > 0.35F) continue;
            float e = (float) Math.sin(Math.PI * lt / 0.35F);
            int hs = hash(idx, 77 + c);
            int a = (int) (e * (100 + 155 * p));
            if (a < 6) continue;
            int x = 4 + Math.floorMod(hs, Math.max(1, w - 8)), y = 4 + Math.floorMod(hs >>> 11, Math.max(1, h - 8));
            int segs = 10 + Math.floorMod(hs >>> 5, 12) + (int) (p * 10);
            for (int s = 0; s < segs; s++) {
                int r = hash(idx * 31 + s, c);
                int dx = (r & 3) - 1, dy = ((r >>> 2) & 3) - 1;
                if (dx == 0 && dy == 0) dx = 1;
                x += dx; y += dy;
                rect(g, x, y, x + 1, y + 1, (a << 24) | pal[1]);
            }
        }

        // 3. edge energy running around the inner rim (from roughly the first tier-1 crystals upward)
        if (p > 0.10F) {
            float ep = (p - 0.10F) / 0.90F;
            int per = 2 * ((w - 6) + (h - 6));
            int len = 18 + (int) (40 * ep);
            int a0 = (int) (70 + 175 * ep);
            int runners = p > 0.5F ? 2 : 1;
            for (int rIdx = 0; rIdx < runners; rIdx++) {
                double speed = 0.035 + 0.055 * ep;   // px per ms
                int head = (int) ((ms * speed) % per);
                if (rIdx == 1) head = per - 1 - head;   // second runner goes the other way
                for (int k = 0; k < len; k++) {
                    int s = rIdx == 0 ? head - k : head + k;
                    int[] pt = rim(Math.floorMod(s, per), w, h);
                    int a = (int) (a0 * (1F - k / (float) len));
                    if (a < 5) continue;
                    int col = k == 0 ? 0xFFFFFF : pal[rIdx == 0 ? 0 : 2];
                    rect(g, pt[0], pt[1], pt[0] + (k == 0 ? 2 : 1), pt[1] + (k == 0 ? 2 : 1), (a << 24) | col);
                }
            }
        }

        // 4. diagonal shimmer sweep (tier 2 and up)
        if (p > 0.4F) {
            float sp = (p - 0.4F) / 0.6F;
            long sc = 5500L;
            float lt = (ms % sc) / (float) sc;
            if (lt < 0.40F) {
                float f = lt / 0.40F;
                int a = (int) (Math.sin(Math.PI * f) * (30 + 65 * sp));
                int sx = (int) (-h * 0.5F + f * (w + h * 0.5F));
                for (int y = 3; y < h - 3; y += 2) {
                    int xc = sx + (int) (y * 0.5F);
                    rect(g, xc - 4, y, xc + 4, y + 2, (a << 24) | pal[2]);
                }
            }
        }
    }

    /** Point on the rim inset 3 px, walking clockwise from the top-left corner. */
    private static int[] rim(int s, int w, int h) {
        int iw = w - 6, ih = h - 6;
        if (s < iw) return new int[]{3 + s, 3};
        s -= iw;
        if (s < ih) return new int[]{w - 4, 3 + s};
        s -= ih;
        if (s < iw) return new int[]{w - 4 - s, h - 4};
        s -= iw;
        return new int[]{3, h - 4 - s};
    }

    private static int hash(long a, int b) {
        long x = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        x ^= x >>> 29;
        x *= 0xBF58476D1CE4E5B9L;
        return (int) (x ^ (x >>> 32));
    }

    private UiInfection() {}
}
