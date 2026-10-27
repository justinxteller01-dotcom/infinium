package dev.justin.infinium.client.mixin;

import dev.justin.infinium.Infinium;
import dev.justin.infinium.ToggleGlidePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sneak + right-click with an empty main hand at air (no block/entity within reach) toggles the Infinium chestplate glider.
 *  One toggle per press: holding the button does not re-toggle (re-armed when the use key is released). */
@Mixin(Minecraft.class)
public abstract class MinecraftUseMixin {
    @Shadow public LocalPlayer player;
    @Shadow public HitResult hitResult;
    @Unique private static boolean infinium$armed = true;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void infinium$airToggle(CallbackInfo ci) {
        LocalPlayer p = player;
        if (p == null || !p.isShiftKeyDown() || !p.getMainHandItem().isEmpty()) return;
        if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) return;
        if (!Infinium.isChestplate(p.getItemBySlot(EquipmentSlot.CHEST))) return;
        if (!dev.justin.infinium.client.InfiniumClientConfig.wingsGesture()) return;   // gesture off (only possible with a custom key bound)
        ci.cancel();
        if (!infinium$armed) return;
        infinium$armed = false;
        if (ClientPlayNetworking.canSend(ToggleGlidePayload.TYPE)) ClientPlayNetworking.send(new ToggleGlidePayload(-1, -1, -1));
        p.swing(InteractionHand.MAIN_HAND);
    }
    @Inject(method = "tick", at = @At("HEAD"))
    private void infinium$rearm(CallbackInfo ci) {
        if (!((Minecraft) (Object) this).options.keyUse.isDown()) infinium$armed = true;
    }
}
