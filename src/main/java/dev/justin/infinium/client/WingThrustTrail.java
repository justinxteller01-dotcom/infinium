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
    record S(Vec3 lo, Vec3 li, Vec3 ro, Vec3 ri, long t, int rgb, boolean cut, float bright, long life, Vec3 up) {}
    static final int ASTRAL_RGB = 0x9B4CFF, SING_RGB = 0x621CAC;
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
                boolean ast = !inf && st == InfiniumStyle.ASTRAL, sing = !inf && st == InfiniumStyle.SINGULARITY;
                if (!(cp || inf || cyb || ast || sing)) continue;
                boolean thrust = InfinityFlightClient.thrusting(p);
                ArrayDeque<S> q = TRAILS.get(p.getId());
                if (q == null) TRAILS.put(p.getId(), q = new ArrayDeque<>());
                FootThrusters.Feet f = FootThrusters.get(p);
                Vec3 fwd = p.getLookAngle();
                Vec3 right = fwd.cross(new Vec3(0, 1, 0));
                right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
                double hw = cyb ? 0.06 : cp ? 0.08 : ast || sing ? 0.07 : 0.10;   // ribbon half width per foot
                if (cp || inf) boost(p.getId(), thrust, now);   // keeps the level fresh even when jets aren't drawn (own 1st person)
                float tb = cp || inf ? boostLevel(p.getId()) : thrust && !cyb ? 1F : 0F;
                hw *= 1F + (cp || inf ? 1.0F : 0.5F) * tb;
                int dye = GlowColor.argb(c);
                int rgb = cyb ? CYB_RGB : ast ? ASTRAL_RGB : sing ? SING_RGB : dye != -1 ? dye & 0xFFFFFF : inf ? InfiniumWingsRenderer.INF_THRUST_DEFAULT : InfiniumWingsRenderer.CP_THRUST_DEFAULT;
                boolean cutNow = q.isEmpty() || now - q.peekLast().t() > 200;
                Vec3 l = f.left(), r = f.right();
                if (ast || sing) {   // magic flight: two body-wake ribbons from the torso sides (no boot energy)
                    Vec3 c0 = p.position().add(0, p.getBbHeight() * 0.45, 0);
                    l = c0.subtract(right.scale(0.22)); r = c0.add(right.scale(0.22));
                }
                Vec3 up = fwd.cross(right).normalize().scale(hw);   // second (crossed) ribbon plane
                q.addLast(new S(l.add(right.scale(hw)), l.subtract(right.scale(hw)), r.add(right.scale(hw)), r.subtract(right.scale(hw)),
                    now, rgb, cutNow, 0.8F + 0.2F * tb, cyb ? 350 : cp ? 450 : ast ? 400 : sing ? 450 : 550, up));
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
                        // crossed plane: same ribbon rotated 90 deg so it never vanishes edge-on
                        Vec3 lm0 = mid(a[i - 1].lo(), a[i - 1].li()), lm1 = mid(a[i].lo(), a[i].li());
                        Vec3 rm0 = mid(a[i - 1].ro(), a[i - 1].ri()), rm1 = mid(a[i].ro(), a[i].ri());
                        Vec3 u0 = a[i - 1].up(), u1 = a[i].up();
                        quad(vc, pose, lm0.add(u0), lm0.subtract(u0), lm1.subtract(u1), lm1.add(u1), r, g, b, a0, a1);
                        quad(vc, pose, rm0.add(u0), rm0.subtract(u0), rm1.subtract(u1), rm1.add(u1), r, g, b, a0, a1);
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
            boolean thrust = InfinityFlightClient.thrusting(p);
            if (set < 0) {
                // Astral / Singularity (magic flight, no boots energy): a soft comet glow behind the torso, plus a
                // downward telekinetic energy cone while thrust is held. Singularity adds a dark void sheath.
                boolean ast = st == InfiniumStyle.ASTRAL, sing = st == InfiniumStyle.SINGULARITY;
                if (!ast && !sing) continue;
                Vec3 c0 = p.getPosition(pt).add(0, p.getBbHeight() * 0.45, 0);
                Vec3 fwd = p.getViewVector(pt), right = fwd.cross(new Vec3(0, 1, 0));
                right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
                Vec3 down = fwd.cross(right).normalize();   // body "down" in the glide plane
                if (down.y > 0) down = down.scale(-1);
                Vec3 back = fwd.scale(-1), dn = down;
                int col = ast ? ASTRAL_RGB : SING_RGB;
                float fl = 0.93F + 0.07F * (float) Math.sin(now / 60.0 + p.getId());
                ctx.submitNodeCollector().submitCustomGeometry(ps, RenderTypes.lightning(), (pose, vc) -> {
                    cone(vc, pose, c0.add(back.scale(0.3)), back, 0.22F, 0.7F * fl, col, 110);
                    cone(vc, pose, c0.add(back.scale(0.3)), back, 0.09F, 0.45F * fl, 0xE6CCFF, 150);
                    if (sing) cone(vc, pose, c0.add(back.scale(0.3)), back, 0.32F, 0.5F * fl, 0x0E0612, 120);
                    if (thrust) {
                        cone(vc, pose, c0, dn, 0.28F, 0.8F * fl, col, 150);
                        cone(vc, pose, c0, dn, 0.1F, 0.5F * fl, 0xE6CCFF, 170);
                    }
                });
                continue;
            }
            int dye = GlowColor.argb(c);
            int rgb = set == 0 ? CYB_RGB : dye != -1 ? dye & 0xFFFFFF : inf ? InfiniumWingsRenderer.INF_THRUST_DEFAULT : InfiniumWingsRenderer.CP_THRUST_DEFAULT;
            int core = set == 2 ? 0xF0D8FF : 0xF4E0FF;
            FootThrusters.Feet f = FootThrusters.get(p, p.getPosition(pt));
            // Thrust boost (Cyber Psycho / Infinity only; Cybernetic has no thrust): smooth 0..1 level with a punchy
            // ramp-in (overshoots to 1.25 then settles) and a smooth fade out on release.
            float b = set == 0 ? 0F : boost(p.getId(), thrust, now);
            float punch = b * (1F + 0.25F * Math.max(0F, 1F - (now - BOOST_START.getOrDefault(p.getId(), 0L)) / 220F));
            float flick = 0.92F + 0.08F * (float) Math.sin(now / 35.0 + p.getId());
            if (set == 1) flick += (0.10F + 0.12F * b) * (float) Math.sin(now / 17.0);   // Cyber Psycho: jittery pulse, stronger on thrust
            if (set == 2) flick += 0.12F * b * (float) Math.sin(now / 45.0);           // Infinity: deep slow throb on thrust
            float len = JET_LEN[set] * (1F + 0.9F * punch) * flick, rad = JET_RAD[set] * (1F + 0.45F * punch);
            int al = Math.min(255, 170 + Math.round(70 * b));
            Vec3[][] jets = {{f.left(), f.leftDir()}, {f.right(), f.rightDir()}};
            final int fset = set; final float fb = b;
            long arcSeed = now / 70 + p.getId() * 31L;   // crackle arcs re-roll every 70 ms
            ctx.submitNodeCollector().submitCustomGeometry(ps, RenderTypes.lightning(), (pose, vc) -> {
                for (int ji = 0; ji < 2; ji++) {
                    Vec3[] j = jets[ji];
                    cone(vc, pose, j[0], j[1], rad, len, rgb, al);
                    cone(vc, pose, j[0], j[1], rad * 0.45F, len * (0.55F + 0.15F * fb), core, 230);
                    if (fset == 2) {   // Infinity: dark void sheath, much heavier on thrust, plus an outer purple distortion shell
                        cone(vc, pose, j[0], j[1], rad * (1.5F + 0.6F * fb), len * (0.7F + 0.2F * fb), 0x1A0530, 140 + Math.round(70 * fb));
                        if (fb > 0.05F) cone(vc, pose, j[0].subtract(j[1].scale(0.04)), j[1], rad * 2.3F, len * 0.5F, 0x621CAC, Math.round(70 * fb));
                    }
                    if (fset == 1 && fb > 0.2F) {   // Cyber Psycho: dye-tinted electric arcs flicking around the boot
                        java.util.Random ar = new java.util.Random(arcSeed * 2 + ji);
                        int n = 1 + (fb > 0.7F ? 1 : 0);
                        for (int k = 0; k < n; k++) if (ar.nextFloat() < 0.75F) arc(vc, pose, j[0], j[1], ar, rgb, Math.round(230 * fb));
                    }
                }
            });
        }
    }
    static final Map<Integer, float[]> BOOST = new HashMap<>();   // id -> {level, last ms}
    static final Map<Integer, Long> BOOST_START = new HashMap<>();
    static final float BOOST_IN_MS = 120F, BOOST_OUT_MS = 350F;
    private static float boost(int id, boolean on, long now) {
        if (BOOST.size() > 256) { BOOST.clear(); BOOST_START.clear(); }
        float[] s = BOOST.computeIfAbsent(id, k -> new float[]{0F, now});
        float dt = Math.min(100F, now - s[1]); s[1] = now;
        if (on && s[0] <= 0.01F) BOOST_START.put(id, now);
        s[0] = on ? Math.min(1F, s[0] + dt / BOOST_IN_MS) : Math.max(0F, s[0] - dt / BOOST_OUT_MS);
        float x = s[0]; return x * x * (3 - 2 * x);   // smoothstep
    }
    /** Current smoothed thrust level for trails / particles (0 if never drawn). */
    static float boostLevel(int id) { float[] s = BOOST.get(id); if (s == null) return 0F; float x = s[0]; return x * x * (3 - 2 * x); }
    /** A short jagged lightning arc from the boot sole sideways/outward (thin crossed quads). */
    private static void arc(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose pose, Vec3 o, Vec3 d, java.util.Random ar, int rgb, int a) {
        Vec3 u = d.cross(new Vec3(0, 1, 0)); if (u.lengthSqr() < 1e-4) u = new Vec3(1, 0, 0); u = u.normalize();
        Vec3 w = d.cross(u).normalize();
        double ang = ar.nextDouble() * Math.PI * 2;
        Vec3 out = u.scale(Math.cos(ang)).add(w.scale(Math.sin(ang)));
        Vec3 prev = o.add(out.scale(0.05));
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
        for (int i = 1; i <= 4; i++) {
            Vec3 nx = o.add(out.scale(0.05 + i * 0.07)).add(d.scale(i * 0.06 + (ar.nextDouble() - 0.5) * 0.08))
                .add(u.scale((ar.nextDouble() - 0.5) * 0.08)).add(w.scale((ar.nextDouble() - 0.5) * 0.08));
            float t = 0.012F;
            quad(vc, pose, prev.add(w.scale(t)), prev.subtract(w.scale(t)), nx.subtract(w.scale(t)), nx.add(w.scale(t)), r, g, bl, a, a);
            quad(vc, pose, prev.add(u.scale(t)), prev.subtract(u.scale(t)), nx.subtract(u.scale(t)), nx.add(u.scale(t)), 255, 240, 255, a, a);
            prev = nx;
        }
    }
    private static Vec3 mid(Vec3 a, Vec3 b) { return a.add(b).scale(0.5); }
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
