package dev.justin.infinium.client.mixin;

import dev.justin.infinium.ArmorRemovePayload;
import dev.justin.infinium.ArmorRemoval;
import dev.justin.infinium.client.ArmorRemoveWarningScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R4: every armor-slot input (pickup, shift-click, number key / offhand swap, Q) and hotbar equip-swap while a stack-buff set
 *  is worn is held back and confirmed on ArmorRemoveWarningScreen; the server performs it only after "Remove". */
@Mixin(MultiPlayerGameMode.class)
public abstract class ArmorRemoveGuardMixin {
    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true, require = 1)
    private void infinium$guardClick(int containerId, int slotId, int button, ContainerInput in, Player p, CallbackInfo ci) {
        if (in == ContainerInput.CLONE || in == ContainerInput.QUICK_CRAFT || p.isCreative() || !ArmorRemoval.buffed(p)) return;
        if (slotId < 0 || slotId >= p.containerMenu.slots.size()) return;
        Slot s = p.containerMenu.getSlot(slotId);
        if (!ArmorRemoval.isArmorSlot(s) || !s.hasItem()) return;
        Minecraft mc = Minecraft.getInstance();
        ci.cancel();
        mc.gui.setScreen(new ArmorRemoveWarningScreen(mc.gui.screen(), new ArmorRemovePayload(containerId, slotId, button, in.ordinal(), -1)));
    }
    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void infinium$guardSwap(Player p, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (p.isCreative() || !ArmorRemoval.buffed(p) || !ArmorRemoval.swapsArmor(p, p.getItemInHand(hand))) return;
        Minecraft mc = Minecraft.getInstance();
        cir.setReturnValue(InteractionResult.FAIL);
        mc.gui.setScreen(new ArmorRemoveWarningScreen(mc.gui.screen(), new ArmorRemovePayload(-1, -1, 0, 0, hand.ordinal())));
    }
}
