package dev.justin.infinium.client.anim;

import static dev.justin.infinium.client.anim.SwordAnimTuning.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;

/**
 * Applies the Infinium sword animation. Original code.
 *
 * Slash geometry (matches EnergySlashEntity/EnergySlashRenderer): the crescent's long axis is the
 * camera-right vector rotated about the view direction by the payload roll (ZP(roll) in camera space:
 * roll -35 = "\", +35 = "/", 90 = vertical, 0 = horizontal). For arc moves the arm sweeps around the
 * camera inside the plane spanned by that axis and the view direction, from the configured start end
 * of the axis to the opposite end, so the blade path lies on the emitted crescent.
 */
public final class InfiniumSwordAnimator {
    private InfiniumSwordAnimator() {}

    static float startSide(SwordMove m) {
        return switch (m) {
            case LEFT_CUT -> LEFT_CUT_START;
            case RIGHT_CUT -> RIGHT_CUT_START;
            case OVERHEAD_CLEAVE -> CLEAVE_START;
            default -> SWEEP_START;
        };
    }

    static boolean isArc(SwordMove m) {
        return m == SwordMove.LEFT_CUT || m == SwordMove.RIGHT_CUT || m == SwordMove.OVERHEAD_CLEAVE || m == SwordMove.FLAT_SWEEP;
    }

    /** In-plane sweep angle (deg). Positive turns the forward vector toward the axis' -X end. */
    static float arcAngle(SwordMove m, float w, float k, float windDeg, float strikeDeg) {
        float s0 = startSide(m);
        return s0 * (strikeDeg * k - windDeg * w);
    }

    /** Rotation by angle about the slash-plane normal: ZP(roll) * YP(phi) * ZP(-roll). Identity when phi = 0. */
    static Quaternionf slashPlaneRotation(float rollDeg, float phiDeg) {
        return new Quaternionf()
                .rotateZ((float) Math.toRadians(rollDeg))
                .rotateY((float) Math.toRadians(phiDeg))
                .rotateZ((float) Math.toRadians(-rollDeg));
    }

    public static void applyFirstPerson(PoseStack pose, HumanoidArm arm, SwordMove move, float roll, float swing,
                                        float equipLeft, float time, float walk, float lagYaw, float lagPitch) {
        float s = arm == HumanoidArm.RIGHT ? 1F : -1F;
        float w = SwordAnimCurves.windup(swing);
        float k = SwordAnimCurves.strike(swing);
        float env = Mth.clamp(w + k, 0F, 1F);

        float idle = 1F - (1F - IDLE_WALK_SCALE) * Mth.clamp(walk, 0F, 1F);
        float breath = SwordAnimCurves.breath(time) * IDLE_BREATH_Y * idle;
        float sway = SwordAnimCurves.sway(time) * idle;

        // camera lag (vanilla-like)
        pose.mulPose(Axis.XP.rotationDegrees(lagPitch * CAMERA_LAG_SCALE));
        pose.mulPose(Axis.YP.rotationDegrees(lagYaw * CAMERA_LAG_SCALE));

        float pull = 0F, fwd = 0F, bladeLevel = 0F, bladeRoll = 0F, twist = 0F, plainYaw = 0F, plainPitch = 0F, twirlSpin = 0F, twirlLift = 0F;
        if (swing > 0F) {
            if (isArc(move)) {
                boolean cleave = move == SwordMove.OVERHEAD_CLEAVE;
                float phi = arcAngle(move, w, k, cleave ? CLEAVE_WINDUP_DEG : ARC_WINDUP_DEG, cleave ? CLEAVE_STRIKE_DEG : ARC_STRIKE_DEG);
                pose.mulPose(slashPlaneRotation(roll, phi));
                pull = ARC_CENTER_PULL * env;
                bladeLevel = ARC_BLADE_LEVEL * env;
                bladeRoll = roll * ARC_BLADE_ROLL_FOLLOW * env;
            } else if (move == SwordMove.STAB) {
                fwd = -STAB_THRUST * k + STAB_PULLBACK * w;
                pull = STAB_CENTER_PULL * env;
                bladeLevel = STAB_BLADE_LEVEL * env;
                twist = STAB_TWIST * k;
            } else if (move == SwordMove.TWIRL) {
                float bell = Mth.sin(Mth.PI * swing);
                twirlSpin = TWIRL_SPIN_DEG * SwordAnimCurves.easeInOutCubic(swing);
                pull = TWIRL_CENTER_PULL * bell;
                twirlLift = TWIRL_LIFT * bell;
            } else { // PLAIN
                plainYaw = s * PLAIN_YAW * k;
                plainPitch = PLAIN_PITCH * k;
                fwd = -PLAIN_FORWARD * k;
            }
        }

        // hand position (camera space)
        pose.translate(s * FP_BASE_X * (1F - pull) + s * sway * IDLE_SWAY_X,
                FP_BASE_Y + breath - equipLeft * EQUIP_RISE + twirlLift,
                FP_BASE_Z + fwd);

        // blade orientation around the grip
        pose.translate(0F, -0.1F, 0.1F);
        pose.mulPose(Axis.YP.rotationDegrees(s * FP_HOLD_YAW + plainYaw));
        pose.mulPose(Axis.XP.rotationDegrees(FP_HOLD_PITCH + bladeLevel + plainPitch + twirlSpin + sway * IDLE_SWAY_PITCH + equipLeft * EQUIP_TILT));
        pose.mulPose(Axis.ZP.rotationDegrees(s * FP_HOLD_ROLL + bladeRoll + twist + s * sway * IDLE_SWAY_ROLL + s * equipLeft * EQUIP_SPIN));
        pose.translate(0F, 0.1F, -0.1F);
    }

    /**
     * Third-person: overwrites the holding arm's rotations (called at TAIL of HumanoidModel.setupAnim) and, while a
     * move plays, the torso twist (so the vanilla 6-tick linear body swing never shows on an Infinium sword).
     * Returns the body yaw to apply (0 when idle).
     */
    public static float applyThirdPerson(ModelPart armPart, HumanoidArm arm, float headPitchRad, SwordMove move, float roll,
                                        float swing, float equipLeft, float time) {
        float s = arm == HumanoidArm.RIGHT ? 1F : -1F;
        float w = SwordAnimCurves.windup(swing);
        float k = SwordAnimCurves.strike(swing);
        float env = Mth.clamp(w + k, 0F, 1F);
        float idle = SwordAnimCurves.breath(time) * TP_IDLE_PITCH;

        float restX = headPitchRad * 0.5F + TP_HOLD_PITCH + idle + equipLeft * TP_EQUIP_PITCH;
        float restY = s * TP_HOLD_YAW;
        float tx = restX, ty = restY, tz = 0F;

        if (swing > 0F) {
            if (isArc(move)) {
                // Direction the blade points: forward (0,0,-1) swept by phi inside the slash plane.
                float phi = (float) Math.toRadians(arcAngle(move, w, k, TP_ARC_WINDUP_DEG, TP_ARC_STRIKE_DEG));
                float r = (float) Math.toRadians(roll);
                float x = -Mth.sin(phi) * Mth.cos(r);   // right
                float y = -Mth.sin(phi) * Mth.sin(r);   // up
                float z = Mth.cos(phi);                 // forward
                float elev = (float) Math.asin(Mth.clamp(y, -1F, 1F));
                float azim = (float) Math.atan2(x, z);
                // Model arm: xRot = -PI/2 points forward; more negative raises it; yRot turns it sideways.
                tx = headPitchRad * 0.5F - Mth.HALF_PI - elev;
                ty = -azim * TP_YAW_SIGN;
                // forearm rolls into the cut direction, trailing on the follow-through
                tz = -startSide(move) * s * TP_FOLLOW_ROLL * (k - 0.5F * w);
            } else if (move == SwordMove.STAB) {
                tx = headPitchRad - Mth.HALF_PI + TP_STAB_PULLBACK * w;
                ty = -s * TP_STAB_INWARD * k;            // thrust toward the centre line
            } else if (move == SwordMove.TWIRL) {
                float bell = Mth.sin(Mth.PI * swing);
                float c = Mth.TWO_PI * SwordAnimCurves.easeInOutCubic(swing);
                tx = restX - TP_TWIRL_RAISE + TP_TWIRL_CIRCLE * Mth.cos(c);
                ty = restY + s * TP_TWIRL_CIRCLE * Mth.sin(c);
                tz = s * TP_TWIRL_CIRCLE * Mth.sin(c);
                env = bell;
            } else {
                tx = restX + TP_PLAIN_PITCH * k;
            }
        }
        armPart.xRot = Mth.lerp(env, restX, tx);
        armPart.yRot = Mth.lerp(env, restY, ty);
        armPart.zRot = Mth.lerp(env, 0F, tz);
        if (swing <= 0F || move == SwordMove.PLAIN) return 0F;
        return (armPart.yRot - restY) * TP_BODY_FOLLOW;
    }
}
