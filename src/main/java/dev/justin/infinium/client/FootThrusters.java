package dev.justin.infinium.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** batch1 (Justin final 2026-10-29): foot-thruster anchor points for the Cybernetic / Cyber Psycho / Infinity elytra
 *  flight visuals. While a player model is rendered (WingsLayerMixin, same pose stack as the body), the sole centre and
 *  the leg axis of both legs are read from the posed HumanoidModel legs and stored relative to the player's render
 *  position, so the effects follow the real pose (incl. vanilla fall-flying roll) for the local and remote players.
 *  When no fresh sample exists (own first person: the model is not drawn) an approximation from the look vector is used.
 *  Purely visual, no gameplay state. */
public final class FootThrusters {
    /** Foot sample: [0]=left sole, [1]=right sole (offsets from the player's position), dir = unit leg axis hip->sole. */
    public record Feet(Vec3 left, Vec3 right, Vec3 leftDir, Vec3 rightDir, long t) {}
    private static final Map<Integer, Feet> FEET = new HashMap<>();
    static final long STALE_MS = 200;

    /** Called from WingsLayerMixin before the (now empty) wing render, with the body-space pose stack. */
    public static void capture(PoseStack pose, HumanoidRenderState state, Object parentModel) {
        if (!(state instanceof AvatarRenderState a) || !state.isFallFlying || !(parentModel instanceof HumanoidModel<?> m)) return;
        Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
        Vec3[] sole = new Vec3[2], dir = new Vec3[2];
        ModelPart[] legs = {m.leftLeg, m.rightLeg};
        for (int i = 0; i < 2; i++) {
            pose.pushPose();
            legs[i].translateAndRotate(pose);
            Vector3f hip = pose.last().pose().transformPosition(0F, 0F, 0F, new Vector3f());
            Vector3f s = pose.last().pose().transformPosition(0F, 12F / 16F, 0F, new Vector3f());   // leg box bottom (y down)
            pose.popPose();
            sole[i] = new Vec3(s.x + cam.x - state.x, s.y + cam.y - state.y, s.z + cam.z - state.z);
            Vec3 d = new Vec3(s.x - hip.x, s.y - hip.y, s.z - hip.z);
            if (d.lengthSqr() < 1e-6 || sole[i].lengthSqr() > 9) return;   // sanity: unexpected space -> keep fallback
            dir[i] = d.normalize();
        }
        if (FEET.size() > 256) FEET.clear();
        FEET.put(a.id, new Feet(sole[0], sole[1], dir[0], dir[1], System.currentTimeMillis()));
    }

    /** World-space feet for p: fresh render sample if any, otherwise an approximation (legs trail along -look). */
    public static Feet get(Player p) {
        Feet f = FEET.get(p.getId());
        Vec3 pos = p.position();
        if (f != null && System.currentTimeMillis() - f.t() < STALE_MS)
            return new Feet(pos.add(f.left()), pos.add(f.right()), f.leftDir(), f.rightDir(), f.t());
        Vec3 fwd = p.getLookAngle();
        Vec3 right = fwd.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 back = fwd.scale(-1);
        Vec3 base = pos.add(0, 0.1, 0).add(back.scale(0.15));
        return new Feet(base.subtract(right.scale(0.12)), base.add(right.scale(0.12)), back, back, 0L);
    }
    private FootThrusters() {}
}
