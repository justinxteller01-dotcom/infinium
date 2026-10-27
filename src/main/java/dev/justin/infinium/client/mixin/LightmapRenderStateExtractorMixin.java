package dev.justin.infinium.client.mixin;

import dev.justin.infinium.client.HelmetVisionClient;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies custom 100% illumination lightmap override when Full Bright is toggled ON
 * on eligible helmets (Astral, Singularity, Infinity).
 * Completely eliminates night vision potion distortions (no green hue, no fog pulsing).
 */
@Mixin(LightmapRenderStateExtractor.class)
public class LightmapRenderStateExtractorMixin {
    @Inject(method = "extract", at = @At("TAIL"))
    private void infinium$applyFullBright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (HelmetVisionClient.isFullBrightActive()) {
            state.needsUpdate = true;
            state.brightness = 1.0F;
            state.ambientColor = new Vector3f(1.0F, 1.0F, 1.0F);
            state.skyFactor = 1.0F;
            state.blockFactor = 1.0F;
            state.darknessEffectScale = 0.0F;
            state.nightVisionEffectIntensity = 0.0F;
        }
    }
}
