package dev.justin.infinium.client.mixin;

import dev.justin.infinium.client.InfiniumClientConfig;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 13.8: the 22 Infinium sound toggles mute every source (UI pickup/place, server equip / full-set packets, other players). */
@Mixin(SoundManager.class)
public class SoundManagerMuteMixin {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;", at = @At("HEAD"), cancellable = true)
    private void infinium$mute(SoundInstance s, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (s != null && dev.justin.infinium.client.InfiniumUiSounds.consumeToast(s)) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
            return;
        }
        if (s != null && InfiniumClientConfig.muted(s.getIdentifier())) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
            return;
        }
        if (s != null && dev.justin.infinium.client.InfiniumUiSounds.onSoundStarting((SoundManager) (Object) this, s)) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }
}
