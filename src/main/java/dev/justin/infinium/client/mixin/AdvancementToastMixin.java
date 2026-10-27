package dev.justin.infinium.client.mixin;

import dev.justin.infinium.client.InfiniumUiSounds;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Full-set advancements still toast and announce. The vanilla toast chime after this constructor is swallowed once. */
@Mixin(AdvancementToast.class)
public class AdvancementToastMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void infinium$quiet(AdvancementHolder holder, CallbackInfo ci) {
        if (holder != null && "infinium".equals(holder.id().getNamespace()) && holder.id().getPath().endsWith("_set"))
            InfiniumUiSounds.expectToastSilence();
    }
}
