package dev.justin.infinium;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Small dye-coloured dust burst on a mode change. Other players get it from the server; the actor gets a tiny S2C
 *  packet and the client draws it only when not in first person (so the wearer never sees it in first person). */
public final class ModeFx {
    public static final int PICKAXE = 0, SHOVEL = 1, AXE = 2, WINGS = 3;
    public static final int DEFAULT_RGB = 0xB04CFF;

    public record Payload(int kind, int mode, int rgb) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Infinium.id("mode_fx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Payload::kind, ByteBufCodecs.VAR_INT, Payload::mode, ByteBufCodecs.INT, Payload::rgb, Payload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register() { PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC); }

    public static int rgb(ItemStack s) { int a = GlowColor.argb(s); return a == -1 ? DEFAULT_RGB : a & 0xFFFFFF; }

    /** Distinct small shapes: pickaxe ring at the hand, shovel row of (mode+2) dots, axe rising helix, wings two arcs behind the shoulders. */
    public static List<Vec3> points(int kind, int mode, Vec3 pos, float yRotDeg) {
        List<Vec3> out = new ArrayList<>();
        double yaw = Math.toRadians(yRotDeg);
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 hand = pos.add(fwd.scale(0.55)).add(right.scale(0.3)).add(0, 1.15, 0);
        return shape(kind, mode, hand, pos, fwd, right, out);
    }

    /** First-person tool burst: centerd where the held tool sits on screen (eye + look * 0.7, down/right). */
    public static List<Vec3> firstPerson(int kind, int mode, Vec3 eye, float yRotDeg, float xRotDeg) {
        double yaw = Math.toRadians(yRotDeg), pitch = Math.toRadians(xRotDeg);
        Vec3 look = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 up = right.cross(look).normalize().scale(-1);
        Vec3 hand = eye.add(look.scale(0.7)).add(right.scale(0.32)).add(up.scale(-0.22));
        List<Vec3> out = new ArrayList<>();
        List<Vec3> raw = shape(kind, mode, hand, eye, fwd, right, out);
        for (int i = 0; i < raw.size(); i += 2) { raw.set(i, hand.add(raw.get(i).subtract(hand).scale(0.5))); raw.set(i + 1, raw.get(i + 1).scale(0.5)); } // half size up close
        return raw;
    }

    private static List<Vec3> shape(int kind, int mode, Vec3 hand, Vec3 pos, Vec3 fwd, Vec3 right, List<Vec3> out) {
        // list layout: [pos0, vel0, pos1, vel1, ...]
        if (kind == WINGS) {
            Vec3 back = pos.add(fwd.scale(-0.35)).add(0, 1.35, 0);
            if (mode == 1) {
                // Outward expanding energy arcs on deploy
                for (int s = -1; s <= 1; s += 2) {
                    for (int i = 0; i < 12; i++) {
                        double t = i / 11.0;
                        double span = 0.15 + 0.85 * t;
                        double arc = Math.sin(t * Math.PI) * 0.35;
                        double yOffset = 0.12 * t + arc * 0.5;
                        Vec3 pt = back.add(right.scale(s * span)).add(fwd.scale(-0.15 - arc * 0.4)).add(0, yOffset, 0);
                        Vec3 vel = right.scale(s * 0.14).add(fwd.scale(-0.06)).add(0, 0.03, 0);
                        out.add(pt);
                        out.add(vel);
                    }
                }
            } else {
                // Inward vacuum implosion stream on retract
                for (int s = -1; s <= 1; s += 2) {
                    for (int i = 0; i < 12; i++) {
                        double t = i / 11.0;
                        double span = 0.25 + 0.85 * (1.0 - t * 0.6);
                        double yOffset = 0.20 * (1.0 - t);
                        Vec3 pt = back.add(right.scale(s * span)).add(fwd.scale(-0.25)).add(0, yOffset, 0);
                        Vec3 toCenter = back.subtract(pt).normalize().scale(0.18);
                        out.add(pt);
                        out.add(toCenter);
                    }
                }
            }
            return out;
        }
        // tools: random burst, 6-10 dust at random spots around the held tool with random outward velocities
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        int n = 6 + r.nextInt(5);
        for (int i = 0; i < n; i++) {
            Vec3 dir = new Vec3(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1));
            if (dir.lengthSqr() < 1e-4) dir = new Vec3(0, 1, 0);
            dir = dir.normalize();
            out.add(hand.add(dir.scale(r.nextDouble(0.05, 0.25))));
            out.add(dir.scale(r.nextDouble(0.01, 0.06)).add(0, r.nextDouble(0, 0.02), 0));
        }
        return out;
    }

    public static DustParticleOptions dust(int rgb) { return new DustParticleOptions(rgb, 0.6F); }
    /** Dye palette for the mode-change burst (blue / light blue / cyan left out: no blue or cyan anywhere). */
    private static final int[] DYES;
    static { java.util.List<Integer> l = new ArrayList<>();
        for (net.minecraft.world.item.DyeColor c : net.minecraft.world.item.DyeColor.values())
            if (c != net.minecraft.world.item.DyeColor.BLUE && c != net.minecraft.world.item.DyeColor.LIGHT_BLUE && c != net.minecraft.world.item.DyeColor.CYAN) l.add(c.getTextureDiffuseColor() & 0xFFFFFF);
        DYES = l.stream().mapToInt(Integer::intValue).toArray(); }
    public static DustParticleOptions randomDye() { return dust(DYES[java.util.concurrent.ThreadLocalRandom.current().nextInt(DYES.length)]); }

    public static void burst(ServerPlayer actor, int kind, int mode, int rgb) {
        ServerLevel level = actor.level();
        DustParticleOptions d = dust(rgb);
        List<Vec3> pts = points(kind, mode, actor.position(), actor.getYRot());
        for (ServerPlayer p : level.players()) {
            if (p == actor || p.distanceToSqr(actor) > 48 * 48) continue;
            for (int i = 0; i < pts.size(); i += 2) { Vec3 v = pts.get(i), w = pts.get(i + 1); // count 0: offsets = velocity
                level.sendParticles(p, kind == WINGS ? d : randomDye(), false, false, v.x, v.y, v.z, 0, w.x, w.y, w.z, 1.0); }
        }
        if (ServerPlayNetworking.canSend(actor, Payload.TYPE)) ServerPlayNetworking.send(actor, new Payload(kind, mode, rgb));
    }

    public static void burst(Player actor, int kind, int mode, ItemStack stack) {
        if (actor instanceof ServerPlayer sp) burst(sp, kind, mode, rgb(stack));
    }
    /** Server+client: true when nothing (block) is within the player's block interaction range along the look vector. */
    /** Shield in either hand: tool mode toggles are blocked so vanilla shield/offhand use runs instead. */
    public static boolean holdingShield(Player p) {
        return isShield(p.getMainHandItem()) || isShield(p.getOffhandItem());
    }

    private static boolean isShield(ItemStack s) {
        return !s.isEmpty() && (s.getItem() instanceof net.minecraft.world.item.ShieldItem
                || s.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS));
    }

    public static boolean aimingAtAir(net.minecraft.world.level.Level level, Player p) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getViewVector(1F).scale(p.blockInteractionRange()));
        var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) return false;
        var box = p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        return net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, end, box, e -> !e.isSpectator() && e.isPickable(), p.entityInteractionRange() * p.entityInteractionRange()) == null;
    }
}
