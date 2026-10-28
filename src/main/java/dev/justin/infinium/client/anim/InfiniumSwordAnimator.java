package dev.justin.infinium.client.anim;

import static dev.justin.infinium.client.anim.SwordAnimTuning.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/** Applies the Infinium sword animation to a first-person PoseStack or a third-person arm. Original code. */
public final class InfiniumSwordAnimator {
    private InfiniumSwordAnimator() {}

    /**
     * First-person transform. Call with the pose already at camera space; afterwards render the
     * item with the FIRST_PERSON_*_HAND display context.
     * @param lagYaw/lagPitch camera-turn lag in degrees (vanilla xBob/yBob delta)
     */
    public static void applyFirstPerson(PoseStack pose, HumanoidArm arm, float swing, float side,
                                        float equipLeft, float time, float walk, float lagYaw, float lagPitch) {
        float s = arm == HumanoidArm.RIGHT ? 1F : -1F;
        float w = SwordAnimCurves.windup(swing);
        float k = SwordAnimCurves.strike(swing);
        float dir = side * s;

        float idle = 1F - (1F - IDLE_WALK_SCALE) * Mth.clamp(walk, 0F, 1F);
        float breath = SwordAnimCurves.breath(time) * IDLE_BREATH_Y * idle;
        float sway = SwordAnimCurves.sway(time) * idle;

        // base hold + idle
        pose.translate(s * FP_BASE_X + s * sway * IDLE_SWAY_X,
                FP_BASE_Y + breath - equipLeft * EQUIP_RISE - k * STRIKE_DROP,
                FP_BASE_Z + w * WINDUP_BACK - k * STRIKE_FORWARD);
        pose.translate(-dir * k * STRIKE_SIDE, 0F, 0F);

        // camera lag
        pose.mulPose(Axis.XP.rotationDegrees(lagPitch * CAMERA_LAG_SCALE));
        pose.mulPose(Axis.YP.rotationDegrees(lagYaw * CAMERA_LAG_SCALE));

        // pivot near the grip so the blade sweeps an arc
        pose.translate(0F, -0.1F, 0.1F);
        pose.mulPose(Axis.YP.rotationDegrees(s * FP_HOLD_YAW + dir * (w * WINDUP_YAW + k * STRIKE_YAW)));
        pose.mulPose(Axis.XP.rotationDegrees(FP_HOLD_PITCH + w * WINDUP_PITCH + k * STRIKE_PITCH
                + sway * IDLE_SWAY_PITCH + equipLeft * EQUIP_TILT));
        pose.mulPose(Axis.ZP.rotationDegrees(s * FP_HOLD_ROLL + dir * (w * WINDUP_ROLL + k * STRIKE_ROLL)
                + s * sway * IDLE_SWAY_ROLL + s * equipLeft * EQUIP_SPIN));
        pose.translate(0F, 0.1F, -0.1F);
    }

    /** Third-person: overwrites the holding arm's rotations (called after vanilla/HumanoidModel setup). */
    public static void applyThirdPerson(ModelPart armPart, HumanoidArm arm, float headPitchRad,
                                        float swing, float side, float equipLeft, float time) {
        float s = arm == HumanoidArm.RIGHT ? 1F : -1F;
        float w = SwordAnimCurves.windup(swing);
        float k = SwordAnimCurves.strike(swing);
        float dir = side * s;
        float idle = SwordAnimCurves.breath(time) * TP_IDLE_PITCH;

        armPart.xRot = headPitchRad * 0.5F + TP_HOLD_PITCH + idle
                + w * TP_WINDUP_PITCH + k * TP_STRIKE_PITCH * (1F - w) + equipLeft * TP_EQUIP_PITCH;
        armPart.yRot = s * TP_HOLD_YAW + dir * (w * TP_WINDUP_YAW + k * TP_STRIKE_YAW);
        armPart.zRot = s * k * TP_STRIKE_ROLL;
    }
}
