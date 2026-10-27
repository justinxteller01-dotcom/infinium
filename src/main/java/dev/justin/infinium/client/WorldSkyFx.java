package dev.justin.infinium.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

/**
 * Client sky effects. No per-frame allocation beyond the particle engine itself.
 *
 * Shooting stars: overworld only, no rain and no thunder, day time 13800-22200 (full night, clear of dawn and dusk).
 * Dome radius 48-72 blocks, continuous luminous trails across the sky dome until exiting the player's view.
 *
 * Daytime ripple: overworld, day time 1000-12000, no rain. Dense purple cosmic ripples expanding across the sky.
 */
public final class WorldSkyFx {
    private static final int MAX = 12;
    private static final boolean[] ON = new boolean[MAX];
    private static final double[] X = new double[MAX], Y = new double[MAX], Z = new double[MAX];
    private static final double[] VX = new double[MAX], VY = new double[MAX], VZ = new double[MAX];
    private static final int[] AGE = new int[MAX], LIFE = new int[MAX];

    // Distant horizon shower meteors (>160m bubble)
    private static final int DISTANT_MAX = 20;
    private static final boolean[] D_ON = new boolean[DISTANT_MAX];
    private static final double[] D_X = new double[DISTANT_MAX], D_Y = new double[DISTANT_MAX], D_Z = new double[DISTANT_MAX];
    private static final double[] D_VX = new double[DISTANT_MAX], D_VY = new double[DISTANT_MAX], D_VZ = new double[DISTANT_MAX];
    private static final int[] D_AGE = new int[DISTANT_MAX], D_LIFE = new int[DISTANT_MAX];
    private static final RandomSource RNG = RandomSource.create();
    // pass2: shared with the menu backdrop (ShootingStarLook); bigger/brighter than R6 (was 0xE8A0FF 3.2 / 0xA14AD0 1.8 x2)
    private static final DustParticleOptions HEAD = new DustParticleOptions(ShootingStarLook.HEAD_RGB, ShootingStarLook.HEAD_SCALE);
    private static final DustParticleOptions TRAIL = new DustParticleOptions(ShootingStarLook.TRAIL_RGB, ShootingStarLook.TRAIL_SCALE);
    private static final DustParticleOptions RIPPLE = new DustParticleOptions(0xA14AD0, 2.5F);
    private static final DustParticleOptions[] RIPPLE_PALETTE = new DustParticleOptions[] {
        new DustParticleOptions(0xA14AD0, 2.6F),
        new DustParticleOptions(0xC45AD4, 2.8F),
        new DustParticleOptions(0x8A3AA0, 2.2F),
        new DustParticleOptions(0xD84CFF, 2.5F)
    };
    private static final DustParticleOptions SHIMMER = new DustParticleOptions(0xB06AD4, 0.7F);
    private static int rippleLeft;
    private static int rippleWait = 140 + RNG.nextInt(160);
    private static float rippleGrow;

    public static void tick(Minecraft mc, Level level) {
        if (mc.player == null || level == null) return;
        boolean overworld = level.dimension() == Level.OVERWORLD;
        boolean rain = level.getRainLevel(1F) > 0F;
        long day = level.getOverworldClockTime() % 24000L;
        boolean night = overworld && !rain && day >= 13800L && day <= 22200L;
        boolean daytime = overworld && !rain && day >= 1000L && day <= 12000L;
        Vec3 at = camera(mc);

        boolean showerActive = MeteorShowerClient.isActive();
        float meteorDens = InfiniumClientConfig.meteorDensity();
        boolean meteorsEnabled = InfiniumClientConfig.shootingStars() && meteorDens > 0.001F;
        if (meteorsEnabled && night && (showerActive ? RNG.nextFloat() < 0.25F * meteorDens : RNG.nextFloat() < (70F / 8400F) * meteorDens)) spawn(at);
        if (showerActive && meteorsEnabled && RNG.nextFloat() < 0.35F * meteorDens) spawnDistant(at);
        shimmer(mc, level, at);
        for (int i = 0; i < MAX; i++) {
            if (!ON[i]) continue;
            X[i] += VX[i]; Y[i] += VY[i]; Z[i] += VZ[i];
            double dx = X[i] - at.x;
            double dy = Y[i] - at.y;
            double dz = Z[i] - at.z;
            double dsq = dx * dx + dy * dy + dz * dz;
            // Traverse continuously across celestial dome until completely clearing player view or horizon
            if (!meteorsEnabled || Y[i] < at.y - 5.0 || dsq > 125.0 * 125.0 || ++AGE[i] > LIFE[i]) {
                ON[i] = false;
                continue;
            }
            // builder: always-visible = 512-block clip (plain addParticle drops anything past 32 blocks)
            // v4: full-bright custom particles (dust was lit by world light, so it looked dim and dark at night)
            level.addAlwaysVisibleParticle(dev.justin.infinium.Infinium.STAR_HEAD, X[i], Y[i], Z[i], 0, 0, 0);
            for (int k = 0; k < ShootingStarLook.TRAIL_POINTS; k++) {
                double f = k * ShootingStarLook.TRAIL_STEP;
                level.addAlwaysVisibleParticle(dev.justin.infinium.Infinium.STAR_TRAIL, X[i] - VX[i] * f, Y[i] - VY[i] * f, Z[i] - VZ[i] * f, 0, 0, 0);
            }
        }

        // Tick distant shower meteors plunging toward the horizon
        for (int i = 0; i < DISTANT_MAX; i++) {
            if (!D_ON[i]) continue;
            D_X[i] += D_VX[i]; D_Y[i] += D_VY[i]; D_Z[i] += D_VZ[i];
            if (!showerActive || !meteorsEnabled || D_Y[i] < at.y - 10.0 || ++D_AGE[i] > D_LIFE[i]) {
                D_ON[i] = false;
                continue;
            }
            level.addAlwaysVisibleParticle(dev.justin.infinium.Infinium.STAR_HEAD, D_X[i], D_Y[i], D_Z[i], 0, 0, 0);
            for (int k = 1; k <= 3; k++) {
                double f = k * 0.3;
                level.addAlwaysVisibleParticle(dev.justin.infinium.Infinium.STAR_TRAIL, D_X[i] - D_VX[i] * f, D_Y[i] - D_VY[i] * f, D_Z[i] - D_VZ[i] * f, 0, 0, 0);
            }
        }

        float rippleDens = InfiniumClientConfig.daytimeSkyRippleDensity();
        if (!InfiniumClientConfig.skyRipple() || rippleDens <= 0.001F || !daytime) return;
        if (rippleLeft > 0) {
            rippleLeft--;
            rippleGrow += 0.95F;
            double y = at.y + 38;
            double baseRad = 8 + rippleGrow;
            float outerTarget = 14 * rippleDens;
            int outerCount = (int) outerTarget;
            if (RNG.nextFloat() < (outerTarget - outerCount)) outerCount++;
            for (int k = 0; k < outerCount; k++) {
                double ang = RNG.nextDouble() * Math.PI * 2;
                double rad = baseRad + (RNG.nextDouble() - 0.5) * 3.0;
                DustParticleOptions dust = RIPPLE_PALETTE[RNG.nextInt(RIPPLE_PALETTE.length)];
                level.addAlwaysVisibleParticle(dust, at.x + Math.cos(ang) * rad, y, at.z + Math.sin(ang) * rad, 0, 0.015, 0);
            }
            if (baseRad > 15) {
                double echoRad = baseRad - 6.5;
                float echoTarget = 6 * rippleDens;
                int echoCount = (int) echoTarget;
                if (RNG.nextFloat() < (echoTarget - echoCount)) echoCount++;
                for (int k = 0; k < echoCount; k++) {
                    double ang = RNG.nextDouble() * Math.PI * 2;
                    double rad = echoRad + (RNG.nextDouble() - 0.5) * 2.0;
                    level.addAlwaysVisibleParticle(RIPPLE_PALETTE[0], at.x + Math.cos(ang) * rad, y - 0.5, at.z + Math.sin(ang) * rad, 0, 0.010, 0);
                }
            }
        } else if (--rippleWait <= 0) {
            rippleLeft = 75;
            rippleGrow = 0;
            rippleWait = 180 + RNG.nextInt(220);
        }
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

    private static void spawn(Vec3 at) {
        int slot = -1;
        for (int i = 0; i < MAX; i++) if (!ON[i]) { slot = i; break; }
        if (slot < 0) return;
        double az = RNG.nextDouble() * Math.PI * 2;
        // pass2: weighted to the top of the dome. 70% high band 50-88 deg (seen looking up), 30% low band 20-36 deg
        // (the only ones that reach the top of a level ~70 deg FOV view). Was uniform 18-75 deg.
        double el = Math.toRadians(RNG.nextFloat() < 0.7F ? 50 + RNG.nextDouble() * 38 : 20 + RNG.nextDouble() * 16);
        double rad = 48 + RNG.nextDouble() * 24;   // builder: dust is ~0.15 x scale blocks; past ~80 blocks it is sub-pixel
        double cos = Math.cos(el);
        X[slot] = at.x + Math.cos(az) * cos * rad;
        Y[slot] = at.y + Math.sin(el) * rad;
        Z[slot] = at.z + Math.sin(az) * cos * rad;
        double dir = RNG.nextBoolean() ? 1.0 : -1.0;
        double tx = -Math.sin(az) * dir;
        double tz = Math.cos(az) * dir;
        VX[slot] = tx * ShootingStarLook.SPEED;
        VY[slot] = -ShootingStarLook.DROP;
        VZ[slot] = tz * ShootingStarLook.SPEED;
        AGE[slot] = 0;
        LIFE[slot] = ShootingStarLook.LIFE_MIN + RNG.nextInt(ShootingStarLook.LIFE_RAND);
        ON[slot] = true;
    }

    private static void spawnDistant(Vec3 at) {
        int slot = -1;
        for (int i = 0; i < DISTANT_MAX; i++) if (!D_ON[i]) { slot = i; break; }
        if (slot < 0) return;

        double az = RNG.nextDouble() * Math.PI * 2;
        double rad = 175.0 + RNG.nextDouble() * 125.0; // 175 - 300 blocks away (>160m bubble)
        double height = 65.0 + RNG.nextDouble() * 60.0;

        D_X[slot] = at.x + Math.cos(az) * rad;
        D_Y[slot] = at.y + height;
        D_Z[slot] = at.z + Math.sin(az) * rad;

        // Plunging down towards the horizon
        double slantAngle = RNG.nextDouble() * Math.PI * 2;
        double speedH = 1.4 + RNG.nextDouble() * 0.8;
        D_VX[slot] = Math.cos(slantAngle) * speedH;
        D_VY[slot] = -(1.8 + RNG.nextDouble() * 0.8);
        D_VZ[slot] = Math.sin(slantAngle) * speedH;

        D_AGE[slot] = 0;
        D_LIFE[slot] = 40 + RNG.nextInt(35);
        D_ON[slot] = true;
    }

    private static Vec3 camera(Minecraft mc) {
        Camera cam = mc.gameRenderer.mainCamera(); // javap 26.2: GameRenderer.mainCamera(), Camera.position()
        return cam.position();
    }

    private WorldSkyFx() {}
}
