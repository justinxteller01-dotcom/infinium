package dev.justin.infinium.client.mixin;

import dev.justin.infinium.client.anim.SwordAnimCurves;
import dev.justin.infinium.client.anim.SwordAnimState;
import dev.justin.infinium.client.anim.SwordPoseState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Copies our sword animation clocks into the player render state (third person / other players). */
@Mixin(value = AvatarRenderer.class, priority = 500)
public abstract class SwordAnimAvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void infinium$swordState(Avatar entity, AvatarRenderState state, float pt, CallbackInfo ci) {
        SwordPoseState s = (SwordPoseState) (Object) state;
        if (!SwordAnimState.active(entity)) { s.infinium$setSword(false, 0F, 1F, 0F, 0F); SwordAnimState.ticksSinceEquip(entity, pt); return; }
        float equip = SwordAnimCurves.equip(SwordAnimState.ticksSinceEquip(entity, pt));
        s.infinium$setSword(true, SwordAnimState.swingProgress(entity, pt), SwordAnimState.swingSide(entity), equip, SwordAnimState.clock(entity, pt));
    }
}
