package dev.justin.infinium.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.justin.infinium.EnergySlashEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Renders the Energy Slash:
 * - Slashes: 15-node arched lightsaber crescent optimized for first-person POV.
 * - Stabs: Straight concentrated cylindrical laser lance.
 * Tinted dynamically to match the sword's active dye/style color.
 */
public class EnergySlashRenderer extends EntityRenderer<EnergySlashEntity, EnergySlashRenderState> {
    private static final int SEGMENT_COUNT = EnergySlashEntity.SEGMENT_COUNT; // 15
    private static final float[] U_NODES = new float[SEGMENT_COUNT];

    static {
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            U_NODES[i] = -1.40F + i * 0.20F;
        }
    }

    public EnergySlashRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public EnergySlashRenderState createRenderState() {
        return new EnergySlashRenderState();
    }

    @Override
    public void extractRenderState(EnergySlashEntity entity, EnergySlashRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.activeSegments = entity.getActiveSegments();
        state.roll = entity.getRoll();
        state.yaw = entity.getYRot();
        state.pitch = entity.getXRot();
        state.color = entity.getColor();
        state.isStab = entity.isStab();
        state.tier = entity.getTier();
        state.ageTicks = entity.getAgeTicks();
    }

    @Override
    public void submit(EnergySlashRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        int active = state.activeSegments;
        if (active == 0) return;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-state.pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.roll));

        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, consumer) -> {
            int c = state.color;
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            int tier = state.tier;

            if (state.isStab) {
                renderLaserLance(consumer, pose, r, g, b, tier);
            } else {
                renderArchedLightsaber(consumer, pose, active, r, g, b, tier);
            }
        });

        poseStack.popPose();
    }

    private void renderLaserLance(VertexConsumer consumer, PoseStack.Pose pose, int r, int g, int b, int tier) {
        // Straight piercing beam: length along +Z, cross profile in X/Y scaled by tier
        float zBack = -0.4F;
        float zFront = 1.6F;
        float scale = tier == 1 ? 0.75F : (tier == 2 ? 1.0F : 1.25F);
        int alphaCore = tier == 1 ? 180 : 255;
        int alphaAura = tier == 1 ? 130 : (tier == 2 ? 180 : 220);

        // Core white-hot laser
        int coreR = Math.min(255, r + 140);
        int coreG = Math.min(255, g + 140);
        int coreB = Math.min(255, b + 140);
        float rCore = 0.035F * scale;

        drawRibbonQuad(consumer, pose,
            -rCore, rCore, zBack, coreR, coreG, coreB, alphaCore,
             rCore, rCore, zBack, coreR, coreG, coreB, alphaCore,
             rCore, rCore, zFront, coreR, coreG, coreB, alphaCore,
            -rCore, rCore, zFront, coreR, coreG, coreB, alphaCore);

        drawRibbonQuad(consumer, pose,
            -rCore, -rCore, zBack, coreR, coreG, coreB, alphaCore,
             rCore, -rCore, zBack, coreR, coreG, coreB, alphaCore,
             rCore, -rCore, zFront, coreR, coreG, coreB, alphaCore,
            -rCore, -rCore, zFront, coreR, coreG, coreB, alphaCore);

        // Radiant Outer Aura
        float rAura = 0.11F * scale;
        drawRibbonQuad(consumer, pose,
            -rAura, 0, zBack, r, g, b, 0,
             rAura, 0, zBack, r, g, b, 0,
             rAura, 0, zFront, r, g, b, alphaAura,
            -rAura, 0, zFront, r, g, b, alphaAura);

        drawRibbonQuad(consumer, pose,
            0, -rAura, zBack, r, g, b, 0,
            0,  rAura, zBack, r, g, b, 0,
            0,  rAura, zFront, r, g, b, alphaAura,
            0, -rAura, zFront, r, g, b, alphaAura);
    }

    private void renderArchedLightsaber(VertexConsumer consumer, PoseStack.Pose pose, int active, int r, int g, int b, int tier) {
        // Core white-hot tint
        int coreR = Math.min(255, r + 130);
        int coreG = Math.min(255, g + 130);
        int coreB = Math.min(255, b + 130);

        float thickness = tier == 1 ? 0.75F : (tier == 2 ? 1.0F : 1.25F);
        int coreAlpha = tier == 1 ? 180 : 255;
        int innerAlpha = tier == 1 ? 150 : 220;
        int auraAlpha = tier == 1 ? 90 : (tier == 2 ? 140 : 180);

        float coreH = 0.018F * thickness;
        float auraH = 0.05F * thickness;
        float tailLen = 0.28F * thickness;

        for (int i = 0; i < SEGMENT_COUNT - 1; i++) {
            boolean a1 = (active & (1 << i)) != 0;
            boolean a2 = (active & (1 << (i + 1))) != 0;
            if (!a1 && !a2) continue;

            float u1 = U_NODES[i];
            float u2 = U_NODES[i + 1];

            float x1 = u1;
            float z1 = -0.22F * u1 * u1;
            float x2 = u2;
            float z2 = -0.22F * u2 * u2;

            // 1. Inner Blazing Hot Laser Core
            drawRibbonQuad(consumer, pose,
                x1, coreH, z1 + 0.02F, coreR, coreG, coreB, coreAlpha,
                x2, coreH, z2 + 0.02F, coreR, coreG, coreB, coreAlpha,
                x2, 0.000F, z2 - 0.12F, r, g, b, innerAlpha,
                x1, 0.000F, z1 - 0.12F, r, g, b, innerAlpha);

            drawRibbonQuad(consumer, pose,
                x1, -coreH, z1 + 0.02F, coreR, coreG, coreB, coreAlpha,
                x2, -coreH, z2 + 0.02F, coreR, coreG, coreB, coreAlpha,
                x2, 0.000F, z2 - 0.12F, r, g, b, innerAlpha,
                x1, 0.000F, z1 - 0.12F, r, g, b, innerAlpha);

            // 2. Outer Radiant Corona Aura
            drawRibbonQuad(consumer, pose,
                x1, auraH, z1, r, g, b, auraAlpha,
                x2, auraH, z2, r, g, b, auraAlpha,
                x2, 0.00F, z2 - tailLen, r, g, b, 0,
                x1, 0.00F, z1 - tailLen, r, g, b, 0);

            drawRibbonQuad(consumer, pose,
                x1, -auraH, z1, r, g, b, auraAlpha,
                x2, -auraH, z2, r, g, b, auraAlpha,
                x2, -0.00F, z2 - tailLen, r, g, b, 0,
                x1, -0.00F, z1 - tailLen, r, g, b, 0);
        }
    }

    private static void drawRibbonQuad(VertexConsumer consumer, PoseStack.Pose pose,
                                       float x1, float y1, float z1, int r1, int g1, int b1, int a1,
                                       float x2, float y2, float z2, int r2, int g2, int b2, int a2,
                                       float x3, float y3, float z3, int r3, int g3, int b3, int a3,
                                       float x4, float y4, float z4, int r4, int g4, int b4, int a4) {
        consumer.addVertex(pose, x1, y1, z1).setColor(r1, g1, b1, a1);
        consumer.addVertex(pose, x2, y2, z2).setColor(r2, g2, b2, a2);
        consumer.addVertex(pose, x3, y3, z3).setColor(r3, g3, b3, a3);
        consumer.addVertex(pose, x4, y4, z4).setColor(r4, g4, b4, a4);
    }
}
