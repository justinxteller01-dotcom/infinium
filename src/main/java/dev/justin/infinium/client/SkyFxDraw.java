package dev.justin.infinium.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * sky-v7: immediate sky-pass draw for WorldSkyFx, mirroring SkyRenderer.renderSun (26.2 bytecode):
 * model-view stack * pose -> DynamicUniforms.writeTransform(mat, color) -> createRenderPass(colour, depth view)
 * -> RenderPipelines.CELESTIAL (core/position_tex, BlendFunction.OVERLAY = SRC_ALPHA,ONE additive, no depth write).
 * Using the sun/moon pipeline keeps the draw TEXTURED, so Iris treats it as gbuffers_skytextured.
 * CELESTIAL has no vertex colour, so geometry is batched per RGB (ColorModulator) and per-vertex alpha is encoded
 * in the UV into textures/misc/sky_fx.png (rows 0-31: strip, alpha = u * soft edge; rows 40-55: 16 soft glow
 * tiles at alpha (i+1)/16).
 */
public final class SkyFxDraw {
    private static final Identifier TEX = Identifier.fromNamespaceAndPath("infinium", "textures/misc/sky_fx.png");
    private static final float TW = 256F, TH = 64F;

    public static RenderTarget target;   // SkyRenderer.renderTarget, supplied by the mixin

    public static void draw(PoseStack poseStack, Consumer<WorldSkyFx.Sink> body) {
        if (target == null) return;
        Map<Integer, BufferBuilder> byColor = new LinkedHashMap<>();
        Map<Integer, ByteBufferBuilder> bytes = new LinkedHashMap<>();
        Map<Integer, int[]> quads = new LinkedHashMap<>();
        try {
            body.accept((p0, p1, p2, p3, rgb, a0, a1, glow) -> {
                a0 = Math.max(0, Math.min(255, a0)); a1 = Math.max(0, Math.min(255, a1));
                if (a0 <= 2 && a1 <= 2) return;
                BufferBuilder bb = byColor.get(rgb);
                if (bb == null) {
                    ByteBufferBuilder bbb = new ByteBufferBuilder(8192);
                    bytes.put(rgb, bbb);
                    bb = new BufferBuilder(bbb, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
                    byColor.put(rgb, bb);
                    quads.put(rgb, new int[1]);
                }
                float[] u = new float[4], v = new float[4];
                if (glow) {
                    int tile = Math.max(0, Math.min(15, Math.round(a0 / 255F * 16F) - 1));
                    float uL = (16 * tile + 0.5F) / TW, uR = (16 * tile + 15.5F) / TW, vT = 40.5F / TH, vB = 55.5F / TH;
                    u = new float[] {uL, uR, uR, uL}; v = new float[] {vT, vT, vB, vB};
                } else {
                    float ua = (0.5F + a0) / TW, ub = (0.5F + a1) / TW, vL = 0.5F / TH, vR = 31.5F / TH;
                    u = new float[] {ua, ua, ub, ub}; v = new float[] {vL, vR, vR, vL};
                }
                float[][] p = {p0, p1, p2, p3};
                for (int k = 0; k < 4; k++) bb.addVertex(p[k][0], p[k][1], p[k][2]).setUv(u[k], v[k]);
                for (int k = 3; k >= 0; k--) bb.addVertex(p[k][0], p[k][1], p[k][2]).setUv(u[k], v[k]);
                quads.get(rgb)[0] += 2;
            });
            if (byColor.isEmpty()) return;
            AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(TEX);
            Matrix4f mv = new Matrix4f(RenderSystem.getModelViewStack()).mul(poseStack.last().pose());
            for (var e : byColor.entrySet()) {
                int rgb = e.getKey(), n = quads.get(rgb)[0];
                MeshData mesh = e.getValue().build();
                if (mesh == null) continue;
                try (mesh) {
                    GpuBuffer vb = RenderSystem.getDevice().createBuffer(() -> "infinium sky fx", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                    try {
                        Vector4f col = new Vector4f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, 1F);
                        GpuBufferSlice tr = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(mv), col);
                        RenderSystem.AutoStorageIndexBuffer idx = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                        GpuBuffer ib = idx.getBuffer(n * 6);
                        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "infinium sky fx",
                                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
                            pass.setPipeline(RenderPipelines.CELESTIAL);
                            RenderSystem.bindDefaultUniforms(pass);
                            pass.setUniform("DynamicTransforms", tr);
                            pass.bindTexture("Sampler0", tex.getTextureView(), tex.getSampler());
                            pass.setVertexBuffer(0, vb.slice());
                            pass.setIndexBuffer(ib, idx.type());
                            pass.drawIndexed(n * 6, 1, 0, 0, 0);
                        }
                    } finally {
                        vb.close();
                    }
                }
            }
        } finally {
            for (ByteBufferBuilder b : bytes.values()) b.close();
        }
    }

    private SkyFxDraw() {}
}
