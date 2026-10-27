package dev.justin.infinium.client.mixin;

import dev.justin.infinium.client.VariableJump;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** G4b: cut the local player's takeoff to vanilla power; holding jump restores the perk height (see VariableJump). */
@Mixin(LivingEntity.class)
public abstract class VariableJumpLivingMixin {
    @Inject(method = "jumpFromGround", at = @At("RETURN"))
    private void infinium$variableJump(CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer p) VariableJump.onJump(p);
    }
}
