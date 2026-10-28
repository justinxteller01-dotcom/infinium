package dev.justin.infinium.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.justin.infinium.client.anim.InfiniumSwordAnimator;
import dev.justin.infinium.client.anim.SwordAnimCurves;
import dev.justin.infinium.client.anim.SwordAnimState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person override for Infinium swords. priority 500 (< default 1000) so this HEAD callback
 * is placed before other mods' HEAD callbacks (e.g. Punchy's own first-person renderer); when an
 * Infinium sword is in the main hand we draw the hands ourselves and cancel, so nothing later in
 * the method (vanilla or other mods' HEAD/RETURN hooks) runs for that frame. Any other item: untouched.
 */
@Mixin(value = ItemInHandRenderer.class, priority = 500)
public abstract class SwordAnimItemInHandMixin {
    @Shadow private float offHandHeight;
    @Shadow private float oOffHandHeight;
    @Shadow private ItemStack offHandItem;

    @Shadow public abstract void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, PoseStack pose, SubmitNodeCollector collector, int light);
    @Shadow protected abstract void submitArmWithItem(AbstractClientPlayer player, float pt, float pitch, InteractionHand hand, float swing,
                                                      ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light);

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void infinium$swordHands(float pt, PoseStack pose, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
        if (player == null || !SwordAnimState.active(player) || player.isUsingItem() || player.isScoping()) return;
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        HumanoidArm arm = player.getMainArm();

        float swing = SwordAnimState.swingProgress(player, pt);
        float equip = SwordAnimCurves.equip(SwordAnimState.ticksSinceEquip(player, pt));
        float time = SwordAnimState.clock(player, pt);
        float walk = (float) Math.min(1.0, player.getDeltaMovement().horizontalDistance() * 5.0);
        float lagPitch = player.getViewXRot(pt) - Mth.lerp(pt, player.xBobO, player.xBob);
        float lagYaw = player.getViewYRot(pt) - Mth.lerp(pt, player.yBobO, player.yBob);

        pose.pushPose();
        InfiniumSwordAnimator.applyFirstPerson(pose, arm, SwordAnimState.move(player), SwordAnimState.roll(player), swing, equip, time, walk, lagYaw, lagPitch);
        renderItem(player, main, arm == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                pose, collector, light);
        pose.popPose();

        // Off hand keeps vanilla behaviour (shield, torch, ...). Note: other mods hooking submitArmWithItem still apply here.
        ItemStack off = this.offHandItem;
        if (off != null && !off.isEmpty()) {
            float offSwing = player.swingingArm == InteractionHand.OFF_HAND ? player.getAttackAnim(pt) : 0F;
            float offEquip = 1F - Mth.lerp(pt, this.oOffHandHeight, this.offHandHeight);
            pose.pushPose();
            submitArmWithItem(player, pt, player.getXRot(pt), InteractionHand.OFF_HAND, offSwing, off, offEquip, pose, collector, light);
            pose.popPose();
        }
        ci.cancel();
    }
}
