package dev.justin.infinium.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.justin.infinium.client.SkyFxDraw;
import dev.justin.infinium.client.WorldSkyFx;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * sky-v7: draws Infinium shooting stars / daytime ripples inside the vanilla sky pass, at the end of
 * SkyRenderer.renderSunMoonAndStars(PoseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase,
 * float rainBrightness, float starBrightness) (26.2 javap). require = 0: if missing, WorldSkyFx logs and falls
 * back to the WORLD pass automatically (heartbeat never fires).
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Shadow @Final private RenderTarget renderTarget;

    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"), require = 0)
    private void infinium$skyFx(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase phase,
                                float rainBrightness, float starBrightness, CallbackInfo ci) {
        WorldSkyFx.skyHookApplied = true;
        SkyFxDraw.target = this.renderTarget;
        WorldSkyFx.renderSky(poseStack, rainBrightness, starBrightness);
    }
}
