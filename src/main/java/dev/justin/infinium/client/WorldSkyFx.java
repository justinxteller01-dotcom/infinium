package dev.justin.infinium.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

/**
 * Client sky effects, SKY-SPACE (sky-v6). Shooting stars and daytime ripples are no longer particles: they are
 * simulated as camera-relative offsets (the player's position is never added), and drawn in COLLECT_SUBMITS with
 * the level pose (view rotation only, no camera translation), each point pushed out along its direction to
 * SKY_R blocks and its size scaled to keep the same angular size. Walking therefore produces zero parallax,
 * exactly like vanilla sun/moon/stars. Terrain occludes them through the normal depth test.
 *
 * Shooting stars: overworld only, no rain/thunder, day time 13800-22200. Look = pre-v5 values (restored),
 * lifespan ~1.75x. Daytime ripple: overworld, day time 1000-12000, no rain. Underwater shimmer stays particles.
 */
public final class WorldSkyFx {
    // pre-a3416c1 look (restored on request); menu backdrop keeps ShootingStarLook's v5 values.
    private static final double HEAD_BLOCKS = 1.6, TRAIL_BLOCKS = 1.2, SPEED = 1.85, DROP = 0.22;
    private static final int TRAIL_ALPHA = 240, TRAIL_POINTS = 3;
    private static final int LIFE_MIN = 166, LIFE_RAND = 79;      // ~1.75x the old 95 + 45
    private static final int D_LIFE_MIN = 70, D_LIFE_RAND = 61;   // ~1.75x the old 40 + 35
    private static final int HEAD_RGB = ShootingStarLook.HEAD_RGB, TRAIL_RGB = ShootingStarLook.TRAIL_RGB;

    private static final int MAX = 12;
    private static final boolean[] ON = new boolean[MAX];
    private static final double[] X = new double[MAX], Y = new double[MAX], Z = new double[MAX];
    private static final double[] VX = new double[MAX], VY = new double[MAX], VZ = new double[MAX];
    private static final int[] AGE = new int[MAX], LIFE = new int[MAX];

    private static final int DISTANT_MAX = 20;
    private static final boolean[] D_ON = new boolean[DISTANT_MAX];
    private static final double[] D_X = new double[DISTANT_MAX], D_Y = new double[DISTANT_MAX], D_Z = new double[DISTANT_MAX];
    private static final double[] D_VX = new double[DISTANT_MAX], D_VY = new double[DISTANT_MAX], D_VZ = new double[DISTANT_MAX];
    private static final int[] D_AGE = new int[DISTANT_MAX], D_LIFE = new int[DISTANT_MAX];
    private static final RandomSource RNG = RandomSource.create();
    private static final DustParticleOptions SHIMMER = new DustParticleOptions(0xB06AD4, 0.7F);
    private static final int[] RIPPLE_RGB = {0xA14AD0, 0xC45AD4, 0x8A3AA0, 0xD84CFF};
    private static int rippleLeft;
    private static int rippleWait = 140 + RNG.nextInt(160);
    private static float rippleGrow;
    private static float rippleDens;
    private static boolean rippleShow;

    /** Called each client tick: pure simulation in camera-relative sky space (origin = camera, never translated). */
    public static void tick(Minecraft mc, Level level) {
        if (mc.player == null || level == null) return;
        boolean overworld = level.dimension() == Level.OVERWORLD;
        boolean rain = level.getRainLevel(1F) > 0F;
        long day = level.getOverworldClockTime() % 24000L;
        boolean night = overworld && !rain && day >= 13800L && day <= 22200L;
        boolean daytime = overworld && !rain && day >= 1000L && day <= 12000L;

        boolean showerActive = MeteorShowerClient.isActive();
        float meteorDens = InfiniumClientConfig.meteorDensity();
        boolean meteorsEnabled = overworld && InfiniumClientConfig.shootingStars() && meteorDens > 0.001F;
        if (meteorsEnabled && night && (showerActive ? RNG.nextFloat() < 0.25F * meteorDens : RNG.nextFloat() < (70F / 8400F) * meteorDens)) spawn();
        if (showerActive && meteorsEnabled && RNG.nextFloat() < 0.35F * meteorDens) spawnDistant();
        shimmer(mc, level, camera(mc));
        for (int i = 0; i < MAX; i++) {
            if (!ON[i]) continue;
            X[i] += VX[i]; Y[i] += VY[i]; Z[i] += VZ[i];
            double dsq = X[i] * X[i] + Y[i] * Y[i] + Z[i] * Z[i];
            if (!meteorsEnabled || Y[i] < -5.0 || dsq > 260.0 * 260.0 || ++AGE[i] > LIFE[i]) ON[i] = false;
        }
        for (int i = 0; i < DISTANT_MAX; i++) {
            if (!D_ON[i]) continue;
            D_X[i] += D_VX[i]; D_Y[i] += D_VY[i]; D_Z[i] += D_VZ[i];
            if (!showerActive || !meteorsEnabled || D_Y[i] < -10.0 || ++D_AGE[i] > D_LIFE[i]) D_ON[i] = false;
        }

        rippleDens = InfiniumClientConfig.daytimeSkyRippleDensity();
        rippleShow = InfiniumClientConfig.skyRipple() && rippleDens > 0.001F && daytime;
        if (!rippleShow) { rippleLeft = 0; return; }
        if (rippleLeft > 0) {
            rippleLeft--;
            rippleGrow += 0.95F;
        } else if (--rippleWait <= 0) {
            rippleLeft = 75;
            rippleGrow = 0;
            rippleWait = 180 + RNG.nextInt(220);
        }
    }

    // ---- render mode / fallback (sky-v7) ----
    private static volatile long lastSkyHookNs;      // set every frame the SkyRenderer mixin fires
    private static int worldFramesWithoutHook;
    private static boolean loggedFallback;
    /** Called from SkyRendererMixin's class init when the mixin applied; absent = target missing. */
    public static volatile boolean skyHookApplied;

    private static boolean skyHookAlive() { return System.nanoTime() - lastSkyHookNs < 300_000_000L; }

    /** Iris API via reflection (no compile dep). */
    public static boolean irisShaderPackInUse() {
        try {
            Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object api = c.getMethod("getInstance").invoke(null);
            return (Boolean) c.getMethod("isShaderPackInUse").invoke(api);
        } catch (Throwable t) { return false; }
    }

    private static boolean wantSky() { return InfiniumClientConfig.skyFxMode() != InfiniumClientConfig.SkyFxMode.WORLD; }

    private static boolean anyActive() {
        boolean any = rippleShow && rippleLeft > 0;
        for (int i = 0; i < MAX && !any; i++) any = ON[i];
        for (int i = 0; i < DISTANT_MAX && !any; i++) any = D_ON[i];
        return any;
    }

    private static boolean visibleCamera(Minecraft mc) {
        FogType fluid = mc.gameRenderer.mainCamera().getFluidInCamera();
        if (fluid != FogType.NONE && fluid != FogType.ATMOSPHERIC) return false;   // underwater/lava/snow: sky fogged out
        return mc.player == null || (!mc.player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
            && !mc.player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS));
    }

    /** Emits the geometry of every active element into a sink. */
    private static void emit(Sink sk, double skyR, float pt, float meteorFade, float rippleFade) {
        if (meteorFade > 0.01F) {
            for (int i = 0; i < MAX; i++) if (ON[i])
                star(sk, skyR, X[i] + VX[i] * pt, Y[i] + VY[i] * pt, Z[i] + VZ[i] * pt,
                    VX[i], VY[i], VZ[i], AGE[i] + pt, LIFE[i], TRAIL_POINTS, meteorFade);
            for (int i = 0; i < DISTANT_MAX; i++) if (D_ON[i])
                star(sk, skyR, D_X[i] + D_VX[i] * pt, D_Y[i] + D_VY[i] * pt, D_Z[i] + D_VZ[i] * pt,
                    D_VX[i], D_VY[i], D_VZ[i], D_AGE[i] + pt, D_LIFE[i], 3, meteorFade);
        }
        if (rippleFade > 0.01F && rippleShow && rippleLeft > 0) ripple(sk, skyR, rippleGrow + 0.95F * pt, rippleFade);
    }

    /**
     * SKY path: called from SkyRendererMixin at the TAIL of SkyRenderer.renderSunMoonAndStars (vanilla sky pass,
     * before terrain/DH LODs/clouds; no depth write -> everything later overdraws it). Fixed radius 100 like stars.
     * Drawn TEXTURED through RenderPipelines.CELESTIAL (the sun/moon pipeline: position_tex, additive, no depth)
     * so Iris routes it to gbuffers_skytextured, which Complementary keeps.
     */
    public static void renderSky(com.mojang.blaze3d.vertex.PoseStack poseStack, float rainBrightness, float starBrightness) {
        lastSkyHookNs = System.nanoTime();
        if (!wantSky()) return;
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !visibleCamera(mc) || !anyActive()) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        // meteors: night-only, follow vanilla star brightness (floor so dusk meteors are not invisible)
        float meteorFade = rainBrightness * Math.min(1F, Math.max(starBrightness * 2F, 0.35F));
        // ripples: daytime curve = inverse of starlight
        float rippleFade = rainBrightness * Math.max(0F, Math.min(1F, 1F - starBrightness * 2F));
        try {
            SkyFxDraw.draw(poseStack, sk -> emit(sk, 100.0, pt, meteorFade, rippleFade));
        } catch (Throwable t) {
            if (!loggedFallback) { loggedFallback = true; dev.justin.infinium.Infinium.LOG.warn("[Infinium] sky-pass meteor draw failed, using WORLD pass", t); }
            lastSkyHookNs = 0; skyDrawBroken = true;
        }
    }
    private static boolean skyDrawBroken;

    /** WORLD path (mode WORLD, or automatic fallback when the sky hook is missing/not firing). */
    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            Level level = mc.level;
            if (level == null || level.dimension() != Level.OVERWORLD) return;
            if (wantSky() && !skyDrawBroken) {
                if (skyHookAlive()) { worldFramesWithoutHook = 0; return; }
                if (++worldFramesWithoutHook < 40) return;            // grace period (loading / pause)
                if (!loggedFallback) {
                    loggedFallback = true;
                    dev.justin.infinium.Infinium.LOG.warn("[Infinium] SkyRenderer hook " + (skyHookApplied ? "not firing" : "missing")
                        + " (renderSunMoonAndStars); falling back to WORLD-pass shooting stars");
                }
            }
            if (!visibleCamera(mc) || !anyActive()) return;
            float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            float fade = 1F - level.getRainLevel(pt);   // rain/thunder fade like vanilla stars
            if (fade <= 0.01F) return;
            double far = mc.options.getEffectiveRenderDistance() * 16.0 * 4.0;   // ~vanilla depth far
            final double skyR = Math.min(Math.min(180.0, mc.options.getEffectiveRenderDistance() * 16.0 * 2.5), far * 0.9);
            PoseStack ps = ctx.poseStack();
            ctx.submitNodeCollector().submitCustomGeometry(ps, RenderTypes.lightning(), (pose, vc) ->
                emit((p0, p1, p2, p3, rgb, a0, a1, glow) -> quad(vc, pose, p0, p1, p2, p3, rgb, a0, a1), skyR, pt, fade, fade));
        });
    }

    /** Geometry sink: strip quads (p0,p1 at a0; p2,p3 at a1) or glow billboards. */
    @FunctionalInterface
    interface Sink { void quad(float[] p0, float[] p1, float[] p2, float[] p3, int rgb, int a0, int a1, boolean glow); }

    /** Head billboard + fading tapered streak (replaces the trail particles that each lived TRAIL_FADE_TICKS). */
    private static void star(Sink vc, double skyR, double x, double y, double z,
                             double vx, double vy, double vz, float age, int life, int points, float fade) {
        float lifeFade = Math.min(1F, Math.min(age / 6F, (life - age) / 20F));
        if (lifeFade <= 0F) return;
        float a = fade * lifeFade;
        // streak length: trail particles lived TRAIL_FADE_TICKS; more points per tick -> denser/brighter streak
        double len = Math.min(age, ShootingStarLook.TRAIL_FADE_TICKS);
        int seg = 10;
        double trailW = TRAIL_BLOCKS * 0.5 * Math.min(1.0, points / 3.0 + 0.34);
        for (int s = 0; s < seg; s++) {
            double f0 = len * s / seg, f1 = len * (s + 1) / seg;
            int a0 = (int) (TRAIL_ALPHA * a * (1 - s / (double) seg));
            int a1 = (int) (TRAIL_ALPHA * a * (1 - (s + 1) / (double) seg));
            double w0 = trailW * (1 - 0.7 * s / seg), w1 = trailW * (1 - 0.7 * (s + 1) / seg);
            ribbon(vc, skyR, x - vx * f0, y - vy * f0, z - vz * f0, x - vx * f1, y - vy * f1, z - vz * f1,
                w0, w1, TRAIL_RGB, a0, a1);
        }
        billboard(vc, skyR, x, y, z, HEAD_BLOCKS * 0.5, HEAD_RGB, (int) (255 * a));
        billboard(vc, skyR, x, y, z, HEAD_BLOCKS * 0.22, 0xFFFFFF, (int) (200 * a));   // hot core
    }

    /** Expanding purple ring overhead (formerly dust particles at +38 blocks), drawn as sky-space bands. */
    private static void ripple(Sink vc, double skyR, float grow, float fade) {
        float life = Math.min(1F, Math.min(grow / 8F, rippleLeft / 20F));
        float base = fade * life * Math.min(1F, 0.35F + rippleDens * 0.65F);
        double h = 38.0, rad = 8 + grow;
        band(vc, skyR, h, rad, 1.6, (int) (150 * base));
        if (rad > 15) band(vc, skyR, h - 0.5, rad - 6.5, 1.0, (int) (90 * base));
    }

    private static void band(Sink vc, double skyR, double h, double rad, double w, int alpha) {
        if (alpha <= 2) return;
        int n = 72;
        for (int k = 0; k < n; k++) {
            double t0 = Math.PI * 2 * k / n, t1 = Math.PI * 2 * (k + 1) / n;
            int rgb = RIPPLE_RGB[(k / 6) % RIPPLE_RGB.length];
            float[] p0 = proj(skyR, Math.cos(t0) * (rad - w), h, Math.sin(t0) * (rad - w));
            float[] p1 = proj(skyR, Math.cos(t0) * (rad + w), h, Math.sin(t0) * (rad + w));
            float[] p2 = proj(skyR, Math.cos(t1) * (rad + w), h, Math.sin(t1) * (rad + w));
            float[] p3 = proj(skyR, Math.cos(t1) * (rad - w), h, Math.sin(t1) * (rad - w));
            vc.quad(p0, p1, p2, p3, rgb, alpha, alpha, false);
        }
    }

    /** Offset -> point on the sky sphere of radius skyR (direction preserved, so angular position is unchanged). */
    private static float[] proj(double skyR, double x, double y, double z) {
        double d = Math.sqrt(x * x + y * y + z * z);
        double k = d < 1e-4 ? 0 : skyR / d;
        return new float[] {(float) (x * k), (float) (y * k), (float) (z * k)};
    }

    private static void billboard(Sink vc, double skyR, double x, double y, double z,
                                  double half, int rgb, int alpha) {
        double d = Math.sqrt(x * x + y * y + z * z);
        if (d < 1e-3 || alpha <= 2) return;
        double k = skyR / d, cx = x * k, cy = y * k, cz = z * k, hs = half * k;   // same angular size as at distance d
        double nx = x / d, ny = y / d, nz = z / d;
        double ux = -nz, uy = 0, uz = nx;                // n x up
        double ul = Math.sqrt(ux * ux + uz * uz);
        if (ul < 1e-4) { ux = 1; uz = 0; ul = 1; }
        ux /= ul; uz /= ul;
        double wx = uy * nz - uz * ny, wy = uz * nx - ux * nz, wz = ux * ny - uy * nx;
        float[] p0 = {(float) (cx + (-ux - wx) * hs), (float) (cy + (-uy - wy) * hs), (float) (cz + (-uz - wz) * hs)};
        float[] p1 = {(float) (cx + (ux - wx) * hs), (float) (cy + (uy - wy) * hs), (float) (cz + (uz - wz) * hs)};
        float[] p2 = {(float) (cx + (ux + wx) * hs), (float) (cy + (uy + wy) * hs), (float) (cz + (uz + wz) * hs)};
        float[] p3 = {(float) (cx + (-ux + wx) * hs), (float) (cy + (-uy + wy) * hs), (float) (cz + (-uz + wz) * hs)};
        vc.quad(p0, p1, p2, p3, rgb, alpha, alpha, true);
    }

    private static void ribbon(Sink vc, double skyR, double x0, double y0, double z0,
                               double x1, double y1, double z1, double w0, double w1, int rgb, int a0, int a1) {
        if (a0 <= 2 && a1 <= 2) return;
        double d0 = Math.sqrt(x0 * x0 + y0 * y0 + z0 * z0), d1 = Math.sqrt(x1 * x1 + y1 * y1 + z1 * z1);
        if (d0 < 1e-3 || d1 < 1e-3) return;
        double k0 = skyR / d0, k1 = skyR / d1;
        double ax = x0 * k0, ay = y0 * k0, az = z0 * k0, bx = x1 * k1, by = y1 * k1, bz = z1 * k1;
        double tx = bx - ax, ty = by - ay, tz = bz - az;
        double nx = ax / skyR, ny = ay / skyR, nz = az / skyR;
        double sx = ty * nz - tz * ny, sy = tz * nx - tx * nz, sz = tx * ny - ty * nx;   // side = t x n
        double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-6) return;
        sx /= sl; sy /= sl; sz /= sl;
        double h0 = w0 * k0, h1 = w1 * k1;
        float[] p0 = {(float) (ax - sx * h0), (float) (ay - sy * h0), (float) (az - sz * h0)};
        float[] p1 = {(float) (ax + sx * h0), (float) (ay + sy * h0), (float) (az + sz * h0)};
        float[] p2 = {(float) (bx + sx * h1), (float) (by + sy * h1), (float) (bz + sz * h1)};
        float[] p3 = {(float) (bx - sx * h1), (float) (by - sy * h1), (float) (bz - sz * h1)};
        vc.quad(p0, p1, p2, p3, rgb, a0, a1, false);
    }

    /** Both windings so the quad shows regardless of facing. p0,p1 take alpha a0; p2,p3 take a1. */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float[] p0, float[] p1, float[] p2, float[] p3,
                             int rgb, int a0, int a1) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        float[][] v = {p0, p1, p2, p3}; int[] al = {a0, a0, a1, a1};
        for (int k = 0; k < 4; k++) vc.addVertex(pose, v[k][0], v[k][1], v[k][2]).setColor(r, g, b, Math.max(0, Math.min(255, al[k])));
        for (int k = 3; k >= 0; k--) vc.addVertex(pose, v[k][0], v[k][1], v[k][2]).setColor(r, g, b, Math.max(0, Math.min(255, al[k])));
    }

    /** Client only. A few small purple motes while the camera is in water. Not a sky render. */
    private static void shimmer(Minecraft mc, Level level, Vec3 at) {
        if (!InfiniumClientConfig.underwaterShimmer()) return;
        float density = InfiniumClientConfig.underwaterParticleDensity();
        if (density <= 0.001F) return;
        if (mc.gameRenderer.mainCamera().getFluidInCamera() != FogType.WATER) return;
        if (RNG.nextFloat() > 0.45F) return;
        float target = 2.0F * density;
        int count = (int) target;
        if (RNG.nextFloat() < (target - count)) count++;
        for (int i = 0; i < count; i++) {
            double ox = (RNG.nextDouble() - 0.5) * 2.2;
            double oy = (RNG.nextDouble() - 0.5) * 1.2;
            double oz = (RNG.nextDouble() - 0.5) * 2.2;
            level.addParticle(SHIMMER, at.x + ox, at.y + oy, at.z + oz, 0, 0.012, 0);
        }
    }

    private static void spawn() {
        int slot = -1;
        for (int i = 0; i < MAX; i++) if (!ON[i]) { slot = i; break; }
        if (slot < 0) return;
        double az = RNG.nextDouble() * Math.PI * 2;
        double el = Math.toRadians(RNG.nextFloat() < 0.7F ? 50 + RNG.nextDouble() * 38 : 20 + RNG.nextDouble() * 16);
        double rad = 48 + RNG.nextDouble() * 24;
        double cos = Math.cos(el);
        X[slot] = Math.cos(az) * cos * rad;
        Y[slot] = Math.sin(el) * rad;
        Z[slot] = Math.sin(az) * cos * rad;
        double dir = RNG.nextBoolean() ? 1.0 : -1.0;
        VX[slot] = -Math.sin(az) * dir * SPEED;
        VY[slot] = -DROP;
        VZ[slot] = Math.cos(az) * dir * SPEED;
        AGE[slot] = 0;
        LIFE[slot] = LIFE_MIN + RNG.nextInt(LIFE_RAND);
        ON[slot] = true;
    }

    private static void spawnDistant() {
        int slot = -1;
        for (int i = 0; i < DISTANT_MAX; i++) if (!D_ON[i]) { slot = i; break; }
        if (slot < 0) return;
        double az = RNG.nextDouble() * Math.PI * 2;
        double rad = 175.0 + RNG.nextDouble() * 125.0;
        double height = 65.0 + RNG.nextDouble() * 60.0;
        D_X[slot] = Math.cos(az) * rad;
        D_Y[slot] = height;
        D_Z[slot] = Math.sin(az) * rad;
        double slantAngle = RNG.nextDouble() * Math.PI * 2;
        double speedH = 1.4 + RNG.nextDouble() * 0.8;
        D_VX[slot] = Math.cos(slantAngle) * speedH;
        D_VY[slot] = -(1.8 + RNG.nextDouble() * 0.8);
        D_VZ[slot] = Math.sin(slantAngle) * speedH;
        D_AGE[slot] = 0;
        D_LIFE[slot] = D_LIFE_MIN + RNG.nextInt(D_LIFE_RAND);
        D_ON[slot] = true;
    }

    private static Vec3 camera(Minecraft mc) {
        Camera cam = mc.gameRenderer.mainCamera(); // javap 26.2: GameRenderer.mainCamera(), Camera.position()
        return cam.position();
    }

    private WorldSkyFx() {}
}
