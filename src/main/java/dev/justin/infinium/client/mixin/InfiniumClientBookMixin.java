package dev.justin.infinium.client.mixin;

import dev.justin.infinium.client.guide.InfiniumGuideClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Register in gen_data.py's client mixin list as "InfiniumClientBookMixin". */
@Mixin(dev.justin.infinium.client.InfiniumClient.class)
public abstract class InfiniumClientBookMixin {
    @Inject(method = "onInitializeClient", at = @At("RETURN"), require = 1)
    private void infinium$guideClient(CallbackInfo ci) {
        InfiniumGuideClient.init();
    }
}
