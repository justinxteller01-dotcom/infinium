package dev.justin.infinium.client.ability;

import dev.justin.infinium.ability.XrayPulse;
import dev.justin.infinium.ability.XrayPulsePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client visuals for the M3 X-ray pulse. Only this player sees them (the payload is sent only to them).
 * - an expanding purple shell of particles (the "scan wave"), 12 ticks, 16 particles/tick
 * - each scanned block/entity lights up when the wave reaches it, as a see-through ghost cuboid
 *   (stroke + translucent fill, always on top), solid for HOLD ms then fading out over FADE ms
 * - entity holograms follow the live entity (looked up by id each frame) and drop when it unloads/dies
 * - a few "hologram sparkle" dust particles per tick on random targets near the camera (budgeted, culled)
 */
public final class XrayPulseClient {
    static final long WAVE_MS = 600, HOLD_MS = 4500, FADE_MS = 1500;
    static final int SPARKLES_PER_TICK = 8, RING_PER_TICK = 16, RING_TICKS = 12;
    static final double CULL_SQ = 48 * 48;
    static final int HOSTILE_COLOR = 0xFFFF3050, PLAYER_COLOR = 0xFF40E0FF;

    record Block(BlockPos pos, int color, double dist) {}
    record Mob(int id, int color) {}

    private static List<Block> blocks = List.of();
    private static List<Mob> mobs = List.of();
    private static Vec3 origin = Vec3.ZERO;
    private static int radius = 0;
    private static long startMs = -1;
    private static int ringTick = RING_TICKS;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(XrayPulsePayload.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            var pl = ctx.client().player;
            if (pl == null) return;
            origin = pl.position();
            radius = Math.max(1, p.radius());
            List<Block> b = new ArrayList<>(p.blocks().size());
            for (var h : p.blocks()) {
                BlockPos bp = BlockPos.of(h.pos());
                b.add(new Block(bp, h.color(), Math.sqrt(Vec3.atCenterOf(bp).distanceToSqr(origin))));
            }
            List<Mob> m = new ArrayList<>();
            for (var e : p.mobs()) m.add(new Mob(e.entityId(), e.kind() == XrayPulse.PLAYER ? PLAYER_COLOR : HOSTILE_COLOR));
            blocks = b; mobs = m;
            startMs = System.currentTimeMillis();
            ringTick = 0;
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> clear());

        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            if (startMs < 0) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            long t = System.currentTimeMillis() - startMs;
            if (t > WAVE_MS + HOLD_MS + FADE_MS) { clear(); return; }
            float waveR = radius * Math.min(1F, t / (float) WAVE_MS);
            float fade = t <= WAVE_MS + HOLD_MS ? 1F : 1F - (t - WAVE_MS - HOLD_MS) / (float) FADE_MS;
            Vec3 cam = mc.player.getEyePosition();
            for (Block b : blocks) {
                if (b.dist() > waveR || Vec3.atCenterOf(b.pos()).distanceToSqr(cam) > CULL_SQ) continue;
                Gizmos.cuboid(b.pos(), style(b.color(), fade)).setAlwaysOnTop();
            }
            for (Mob m : mobs) {
                Entity e = mc.level.getEntity(m.id());
                if (e == null || !e.isAlive() || e.distanceToSqr(cam) > CULL_SQ) continue;
                if (e.position().distanceTo(origin) > waveR && t < WAVE_MS) continue;
                Gizmos.cuboid(e.getBoundingBox().inflate(0.05), style(m.color(), fade)).setAlwaysOnTop();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (startMs < 0 || mc.player == null || mc.level == null) return;
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            if (ringTick < RING_TICKS) {   // scan-wave shell
                double rr = radius * (ringTick + 1) / (double) RING_TICKS;
                DustParticleOptions d = new DustParticleOptions(0xB07CFF, 1.4F);
                for (int i = 0; i < RING_PER_TICK; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, yy = rnd.nextDouble() * 2 - 1, s = Math.sqrt(1 - yy * yy);
                    Vec3 q = origin.add(Math.cos(a) * s * rr, 1.0 + yy * rr * 0.5, Math.sin(a) * s * rr);
                    if (q.distanceToSqr(mc.player.getEyePosition()) < CULL_SQ) mc.level.addParticle(d, q.x, q.y, q.z, 0, 0, 0);
                }
                ringTick++;
            }
            if (blocks.isEmpty() || System.currentTimeMillis() - startMs > WAVE_MS + HOLD_MS) return;
            for (int i = 0; i < SPARKLES_PER_TICK; i++) {   // hologram sparkle, budgeted
                Block b = blocks.get(rnd.nextInt(blocks.size()));
                if (Vec3.atCenterOf(b.pos()).distanceToSqr(mc.player.getEyePosition()) > 24 * 24) continue;
                mc.level.addParticle(new DustParticleOptions(b.color() & 0xFFFFFF, 0.7F),
                    b.pos().getX() + rnd.nextDouble(), b.pos().getY() + rnd.nextDouble(), b.pos().getZ() + rnd.nextDouble(), 0, 0.02, 0);
            }
        });
    }

    static GizmoStyle style(int argb, float fade) {
        int a = Math.round(0xE0 * fade), fa = Math.round(0x38 * fade);
        int rgb = argb & 0xFFFFFF;
        return GizmoStyle.strokeAndFill((a << 24) | rgb, 2.0F, (fa << 24) | rgb);
    }

    static void clear() { blocks = List.of(); mobs = List.of(); startMs = -1; ringTick = RING_TICKS; }

    private XrayPulseClient() {}
}
