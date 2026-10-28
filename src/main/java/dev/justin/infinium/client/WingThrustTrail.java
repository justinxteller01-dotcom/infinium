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

/** batch1: Cyber Psycho thrust afterglow. While thrusting, each wing's trailing edge drops a flat ribbon
 *  (laser-blade swing style) that fades out over LIFE_MS. Client only, world-space samples, one per tick. */
public final class WingThrustTrail {
    static final long LIFE_MS = 450;
    record S(Vec3 lo, Vec3 li, Vec3 ro, Vec3 ri, long t, int rgb, boolean cut) {}
    private static final Map<Integer, ArrayDeque<S>> TRAILS = new HashMap<>();

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level == null) { TRAILS.clear(); return; }
            long now = System.currentTimeMillis();
            for (Player p : mc.level.players()) {
                ItemStack c = p.getItemBySlot(EquipmentSlot.CHEST);
                boolean cp = Infinium.isChestplate(c) && InfiniumStyle.get(c) == InfiniumStyle.CYBERPSYCHO;
                ArrayDeque<S> q = TRAILS.get(p.getId());
                if (cp && p.isFallFlying() && InfinityFlightClient.thrusting(p)) {
                    if (q == null) TRAILS.put(p.getId(), q = new ArrayDeque<>());
                    Vec3 fwd = p.getLookAngle();
                    Vec3 right = fwd.cross(new Vec3(0, 1, 0));
                    right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
                    Vec3 back = p.position().add(0, 0.35, 0).subtract(fwd.scale(0.55));
                    int dye = GlowColor.argb(c);
                    boolean cut = q.isEmpty() || now - q.peekLast().t() > 120;
                    q.addLast(new S(back.add(right.scale(1.05)), back.add(right.scale(0.35)),
                        back.subtract(right.scale(1.05)), back.subtract(right.scale(0.35)), now,
                        dye != -1 ? dye & 0xFFFFFF : InfiniumWingsRenderer.CP_THRUST_DEFAULT, cut));
                }
            }
            TRAILS.values().forEach(q -> { while (!q.isEmpty() && now - q.peekFirst().t() > LIFE_MS) q.pollFirst(); });
            TRAILS.values().removeIf(ArrayDeque::isEmpty);
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
            if (TRAILS.isEmpty()) return;
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
                        int a0 = alpha(now - a[i - 1].t()), a1 = alpha(now - a[i].t());
                        quad(vc, pose, a[i - 1].lo(), a[i - 1].li(), a[i].li(), a[i].lo(), r, g, b, a0, a1);
                        quad(vc, pose, a[i - 1].ro(), a[i - 1].ri(), a[i].ri(), a[i].ro(), r, g, b, a0, a1);
                    }
                });
            }
            ps.popPose();
        });
    }
    private static int alpha(long age) { return (int) (200 * Math.max(0, 1 - age / (double) LIFE_MS)); }
    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose pose, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                             int r, int g, int b, int a0, int a1) {
        // both windings so the flat ribbon is visible from either side
        Vec3[] v = {p0, p1, p2, p3}; int[] al = {a0, a0, a1, a1};
        for (int k = 0; k < 4; k++) vc.addVertex(pose, (float) v[k].x, (float) v[k].y, (float) v[k].z).setColor(r, g, b, al[k]);
        for (int k = 3; k >= 0; k--) vc.addVertex(pose, (float) v[k].x, (float) v[k].y, (float) v[k].z).setColor(r, g, b, al[k]);
    }
    private WingThrustTrail() {}
}
