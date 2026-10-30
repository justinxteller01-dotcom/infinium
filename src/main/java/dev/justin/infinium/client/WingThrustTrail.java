package dev.justin.infinium.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.justin.infinium.GlowColor;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumStyle;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** batch1 (2026-10-29, rework): solid boot energy jets (drawJets) + solid trail for Cybernetic / Cyber Psycho / Infinity elytra flight. While fall-flying,
 *  each boot sole (FootThrusters) drops a thin flat ribbon that fades out (Cybernetic modest 260 ms, Cyber Psycho dye
 *  tinted 420 ms, Infinity 600 ms; wider + brighter while thrusting). Client only, world-space samples, one per tick. */
public final class WingThrustTrail {
    record S(Vec3 lo, Vec3 li, Vec3 ro, Vec3 ri, long t, int rgb, boolean cut, float bright, long life) {}
    static final int CYB_RGB = 0xB048F0;
    private static final Map<Integer, ArrayDeque<S>> TRAILS = new HashMap<>();

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level == null) { TRAILS.clear(); return; }
            long now = System.currentTimeMillis();
            for (Player p : mc.level.players()) {
                ItemStack c = p.getItemBySlot(EquipmentSlot.CHEST);
                if (!Infinium.isChestplate(c) || !p.isFallFlying()) continue;
                boolean inf = Infinium.isInfinity(c);
                InfiniumStyle st = InfiniumStyle.get(c);
                boolean cp = !inf && st == InfiniumStyle.CYBERPSYCHO, cyb = !inf && st == InfiniumStyle.CYBERNETIC;
                if (!(cp || inf || cyb)) continue;
                boolean thrust = InfinityFlightClient.thrusting(p);
                ArrayDeque<S> q = TRAILS.get(p.getId());
                if (q == null) TRAILS.put(p.getId(), q = new ArrayDeque<>());
                FootThrusters.Feet f = FootThrusters.get(p);
                Vec3 fwd = p.getLookAngle();
                Vec3 right = fwd.cross(new Vec3(0, 1, 0));
                right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
                double hw = cyb ? 0.06 : cp ? 0.08 : 0.10;   // ribbon half width per foot
                if (thrust) hw *= 1.5;
                int dye = GlowColor.argb(c);
                int rgb = cyb ? CYB_RGB : dye != -1 ? dye & 0xFFFFFF : inf ? InfiniumWingsRenderer.INF_THRUST_DEFAULT : InfiniumWingsRenderer.CP_THRUST_DEFAULT;
                boolean cutNow = q.isEmpty() || now - q.peekLast().t() > 200;
                Vec3 l = f.left(), r = f.right();
                q.addLast(new S(l.add(right.scale(hw)), l.subtract(right.scale(hw)), r.add(right.scale(hw)), r.subtract(right.scale(hw)),
                    now, rgb, cutNow, thrust ? 1F : 0.8F, cyb ? 350 : cp ? 450 : 550));
            }
            TRAILS.values().forEach(q -> { while (!q.isEmpty() && now - q.peekFirst().t() > q.peekFirst().life()) q.pollFirst(); });
            TRAILS.values().removeIf(ArrayDeque::isEmpty);
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
            Vec3 cam = ctx.levelState().cameraRenderState.pos;
            PoseStack ps = ctx.poseStack();
            long now = System.currentTimeMillis();
            ps.pushPose();
            ps.translate(-cam.x, -cam.y, -cam.z);
            for (ArrayDeque<S> q : TRAILS.values()) {
                S[] a = q.toArray(new S[0]);
                ctx.submitNodeCollector().submitCustomGeometry(ps, RenderTypes.lightning(), (pose, vc) -> {
                    for (int i = 1; i < a.length; i++) {
                        if (a[i].cut()) continue;
                        int r = (a[i].rgb() >> 16) & 255, g = (a[i].rgb() >> 8) & 255, b = a[i].rgb() & 255;
                        int a0 = alpha(a[i - 1], now), a1 = alpha(a[i], now);
                        quad(vc, pose, a[i - 1].lo(), a[i - 1].li(), a[i].li(), a[i].lo(), r, g, b, a0, a1);
                        quad(vc, pose, a[i - 1].ro(), a[i - 1].ri(), a[i].ri(), a[i].ro(), r, g, b, a0, a1);
                    }
                });
            }
            drawJets(ctx, ps);
            ps.popPose();
        });
    }
    private static int alpha(S s, long now) { double x = Math.min(1, (now - s.t()) / (double) s.life()); return (int) (235 * s.bright() * (1 - x * x * x)); }
    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose pose, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                             int r, int g, int b, int a0, int a1) {
        // both windings so the flat ribbon is visible from either side
        Vec3[] v = {p0, p1, p2, p3}; int[] al = {a0, a0, a1, a1};
        for (int k = 0; k < 4; k++) vc.addVertex(pose, (float) v[k].x, (float) v[k].y, (float) v[k].z).setColor(r, g, b, al[k]);
        for (int k = 3; k >= 0; k--) vc.addVertex(pose, (float) v[k].x, (float) v[k].y, (float) v[k].z).setColor(r, g, b, al[k]);
    }
    /** Boot energy jets: a solid, smooth glowing cone out of each boot sole along the posed leg axis (FootThrusters),
     *  outer coloured cone + bright inner core, 4 crossed fans so it reads solid from any angle. No nozzle object: the
     *  cone starts flush at the sole. Own first person: skipped (would sit at the camera). */
    static final float[] JET_LEN = {0.55F, 0.75F, 0.9F}, JET_RAD = {0.09F, 0.11F, 0.13F};   // cyb, cp, inf
    private static void drawJets(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx, PoseStack ps) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long now = System.currentTimeMillis();
        for (Player p : mc.level.players()) {
            if (!p.isFallFlying() || p.isInvisible()) continue;
            if (p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) continue;
            ItemStack c = p.getItemBySlot(EquipmentSlot.CHEST);
            if (!Infinium.isChestplate(c)) continue;
            boolean inf = Infinium.isInfinity(c);
            InfiniumStyle st = InfiniumStyle.get(c);
            int set = inf ? 2 : st == InfiniumStyle.CYBERPSYCHO ? 1 : st == InfiniumStyle.CYBERNETIC ? 0 : -1;
            if (set < 0) continue;
            boolean thrust = InfinityFlightClient.thrusting(p);
            int dye = GlowColor.argb(c);
            int rgb = set == 0 ? CYB_RGB : dye != -1 ? dye & 0xFFFFFF : inf ? InfiniumWingsRenderer.INF_THRUST_DEFAULT : InfiniumWingsRenderer.CP_THRUST_DEFAULT;
            int core = set == 2 ? 0xF0D8FF : 0xF4E0FF;
            FootThrusters.Feet f = FootThrusters.get(p, p.getPosition(pt));
            float flick = 0.92F + 0.08F * (float) Math.sin(now / 35.0 + p.getId());
            if (set == 1) flick += 0.10F * (float) Math.sin(now / 17.0);   // Cyber Psycho: harder, jittery pulse
            float len = JET_LEN[set] * (thrust ? 1.6F : 1F) * flick, rad = JET_RAD[set] * (thrust ? 1.25F : 1F);
            Vec3[][] jets = {{f.left(), f.leftDir()}, {f.right(), f.rightDir()}};
            ctx.submitNodeCollector().submitCustomGeometry(ps, RenderTypes.lightning(), (pose, vc) -> {
                for (Vec3[] j : jets) {
                    cone(vc, pose, j[0], j[1], rad, len, rgb, thrust ? 210 : 170);
                    cone(vc, pose, j[0], j[1], rad * 0.45F, len * 0.55F, core, 230);
                    if (set == 2) cone(vc, pose, j[0], j[1], rad * 1.5F, len * 0.7F, 0x1A0530, 140);   // Infinity: dark void sheath
                }
            });
        }
    }
    private static void cone(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose pose, Vec3 o, Vec3 d, float rad, float len, int rgb, int a) {
        Vec3 u = d.cross(new Vec3(0, 1, 0)); if (u.lengthSqr() < 1e-4) u = d.cross(new Vec3(1, 0, 0)); u = u.normalize();
        Vec3 w = d.cross(u).normalize();
        Vec3 tip = o.add(d.scale(len));
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        for (int i = 0; i < 4; i++) {   // 4 crossed fans (8 directions), base opaque-ish -> tip transparent
            double ang = i * Math.PI / 4;
            Vec3 e = u.scale(Math.cos(ang) * rad).add(w.scale(Math.sin(ang) * rad));
            Vec3 p0 = o.add(e), p1 = o.subtract(e);
            Vec3[] v = {p0, p1, tip, tip}; int[] al = {a, a, 0, 0};
            for (int k = 0; k < 4; k++) vc.addVertex(pose, (float) v[k].x, (float) v[k].y, (float) v[k].z).setColor(r, g, b, al[k]);
            for (int k = 3; k >= 0; k--) vc.addVertex(pose, (float) v[k].x, (float) v[k].y, (float) v[k].z).setColor(r, g, b, al[k]);
        }
    }
    private WingThrustTrail() {}
}
