package dev.justin.infinium.client.mixin;

import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumDurability;
import dev.justin.infinium.client.SwordSlashClientHandler;
import dev.justin.infinium.compat.punchy.PunchyBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts Minecraft attack start.
 * When Punchy is NOT loaded, gates energy slash launch on full cooldown and enforces combo progression.
 * When Punchy IS loaded, click firing is bypassed so that the attack fires at the exact moment
 * Punchy actually plays the animation clip.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackMixin {
    @Shadow public LocalPlayer player;
    @Shadow protected int missTime;
    @Shadow public HitResult hitResult;

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void infinium$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        boolean isBlock = this.hitResult != null && this.hitResult.getType() == HitResult.Type.BLOCK;
        SwordSlashClientHandler.setQueuedAttackTargetBlock(isBlock);
        SwordSlashClientHandler.cancelPendingSpin();

        LocalPlayer p = this.player;
        if (p == null || this.missTime > 0 || p.isHandsBusy()) return;
        if (isBlock) return;
        ItemStack main = p.getItemInHand(InteractionHand.MAIN_HAND);
        if (!main.is(Infinium.SWORD) || InfiniumDurability.disabled(main)) return;

        // When Punchy is loaded, do not fire on click; fire when Punchy plays the clip.
        // Without Punchy loaded, keep firing on click directly.
        // Also fire on click when Infinium's own sword animations replace Punchy for this sword.
        if (!PunchyBridge.isPunchyLoaded() || dev.justin.infinium.client.anim.SwordAnimTuning.overridesPunchy()) {
            SwordSlashClientHandler.onStartAttackVanilla(p, main);
        }
    }
}
