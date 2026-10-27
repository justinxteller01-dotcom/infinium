package dev.justin.infinium.compat.mixin.spatial;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import dev.justin.infinium.compat.SpatialMargin;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Spatial GUI overscan, vanilla side (26.2 GuiRenderer.draw / enableScissor, verified with javap). See SpatialMargin. */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererOverscanMixin {
    @WrapOperation(method = "draw", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lnet/minecraft/client/renderer/Projection;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), require = 1)
    private GpuBufferSlice infinium$overscanProjection(ProjectionMatrixBuffer buf, Projection proj, Operation<GpuBufferSlice> op) {
        if (!SpatialMargin.on() || !SpatialMargin.isSpatialRenderer((GuiRenderer) (Object) this)) return op.call(buf, proj);
        float k = 1F / SpatialMargin.F;   // same ortho, shrunk around the center: [0,W] maps to the middle 1/F of the texture
        Matrix4f m = new Matrix4f().scaling(k, k, 1F).mul(proj.getMatrix(new Matrix4f()));
        return buf.getBuffer(m);          // the Matrix4f overload resets the cache, so the next vanilla frame re-uploads
    }

    @WrapOperation(method = "enableScissor", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderPass;enableScissor(IIII)V"), require = 1)
    private void infinium$overscanScissor(RenderPass pass, int x, int y, int w, int h, Operation<Void> op,
                                          @Local(argsOnly = true) ScreenRectangle rect) {
        TextureTarget t = SpatialMargin.isSpatialRenderer((GuiRenderer) (Object) this) ? SpatialMargin.target() : null;
        if (!SpatialMargin.on() || t == null) { op.call(pass, x, y, w, h); return; }
        var win = net.minecraft.client.Minecraft.getInstance().getWindow();
        double gw = (double) win.getGuiScaledWidth(), gh = (double) win.getGuiScaledHeight();
        double F = SpatialMargin.F, px = t.width / (gw * F), py = t.height / (gh * F);   // texture pixels per gui unit
        double mx = gw * (F - 1) / 2, my = gh * (F - 1) / 2;
        int left = (int) Math.floor((rect.left() + mx) * px), right = (int) Math.ceil((rect.right() + mx) * px);
        int top = (int) Math.floor((rect.top() + my) * py), bottom = (int) Math.ceil((rect.bottom() + my) * py);
        op.call(pass, left, t.height - bottom, Math.max(0, right - left), Math.max(0, bottom - top));
    }
}
