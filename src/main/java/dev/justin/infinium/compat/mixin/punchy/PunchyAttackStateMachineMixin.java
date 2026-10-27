package dev.justin.infinium.compat.mixin.punchy;

import dev.justin.infinium.Infinium;
import dev.justin.infinium.client.SwordSlashClientHandler;
import dev.justin.infinium.compat.punchy.PunchyBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import punchy.client.animation.data.AnimationClip;
import punchy.client.state.AttackActionTracker;
import punchy.config.PunchyTuningConfig;

/**
 * Mixin into Punchy's AttackStateMachine.
 * Intercepts Punchy's attack clip playback at the exact moment the animation plays.
 * Firing the arc or beam occurs here, in sync with the animation clip.
 */
@Pseudo
@Mixin(targets = "punchy.client.state.AttackStateMachine", remap = false)
public abstract class PunchyAttackStateMachineMixin {
    @Shadow private int comboIndex;
    private static PunchyTuningConfig.CustomAnimationEntry LAST_ENTRY = null;

    /**
     * Intercepts Punchy's crit check inside playAttack.
     * When targeting a block, does not consume critical from tracker so crit state is preserved.
     * Enforces client throttle check BEFORE returning true to Punchy so a crit animation never plays without a beam.
     * Resolves crit clip using player's preferred Punchy option, falling back safely.
     */
    @Redirect(
        method = "playAttack",
        at = @At(
            value = "INVOKE",
            target = "Lpunchy/client/state/AttackActionTracker;consumeCritical()Z"
        )
    )
    private boolean infinium$onConsumeCritical() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            ItemStack stack = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
            if (stack.is(Infinium.SWORD)) {
                SwordSlashClientHandler.cancelPendingSpin();
                // When targeting a block, don't use up crit: let Punchy keep its crit state as if we hadn't intervened
                if (SwordSlashClientHandler.isQueuedAttackTargetBlock()) {
                    return false;
                }

                // Check client throttle BEFORE returning true to Punchy: if throttled, do not take crit path
                if (!SwordSlashClientHandler.canFireSlash(mc.player)) {
                    return false;
                }

                boolean isCrit = AttackActionTracker.consumeCritical();
                if (isCrit) {
                    PunchyTuningConfig.CustomAnimationEntry entry = PunchyBridge.findAttackEntry(stack);
                    if (entry == null) {
                        entry = LAST_ENTRY;
                    } else {
                        LAST_ENTRY = entry;
                    }
                    AnimationClip critClip = PunchyBridge.resolveCritClip(mc, stack, entry);
                    if (critClip != null) {
                        boolean sent = SwordSlashClientHandler.onPunchyCritAttack(mc, stack);
                        if (sent) {
                            this.comboIndex = 0;
                            return true;
                        }
                    }
                    // Crit clip did not resolve or beam not sent: fall back safely (never both)
                    return false;
                }
                return false;
            }
        }
        return AttackActionTracker.consumeCritical();
    }

    /**
     * Intercepts resolveCustomAttackClip in AttackStateMachine.
     * When queued attack targets a block, leaves mining thrust alone without advancing combo.
     * Resolves clip before sending beam, sets comboIndex to the next stage (Punchy's meaning),
     * and returns the matching clip.
     */
    @Inject(method = "resolveCustomAttackClip", at = @At("HEAD"), cancellable = true)
    private void infinium$overrideSwordClip(
        ItemStack stack,
        InteractionHand hand,
        Minecraft mc,
        PunchyTuningConfig.CustomAnimationEntry entry,
        CallbackInfoReturnable<AnimationClip> cir
    ) {
        if (stack != null && stack.is(Infinium.SWORD)) {
            SwordSlashClientHandler.cancelPendingSpin();
            if (entry != null) {
                LAST_ENTRY = entry;
            }
            // Check the target from the click that was queued: when on a block, let mining thrust play
            if (SwordSlashClientHandler.isQueuedAttackTargetBlock()) {
                return;
            }
            SwordSlashClientHandler.PunchyAttackResult res = SwordSlashClientHandler.onPunchyResolveAttackClip(mc, stack, hand, entry);
            if (res != null) {
                // Keep Punchy's meaning: comboIndex is the NEXT stage to play
                this.comboIndex = res.nextStage();
                cir.setReturnValue(res.clip());
            }
        }
    }
}
