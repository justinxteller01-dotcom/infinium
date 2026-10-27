package dev.justin.infinium.client.mixin;

import dev.justin.infinium.client.VariableJump;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** G4b: per-tick held-jump boost, before this tick's movement (see VariableJump). */
@Mixin(LocalPlayer.class)
public abstract class VariableJumpLocalPlayerMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void infinium$heldJump(CallbackInfo ci) {
        VariableJump.tick((LocalPlayer) (Object) this);
        dev.justin.infinium.client.AirJumpClient.tick((LocalPlayer) (Object) this);   // G4b air jumps
    }
}
