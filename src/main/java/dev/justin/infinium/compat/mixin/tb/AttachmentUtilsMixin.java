package dev.justin.infinium.compat.mixin.tb;
import dev.justin.infinium.compat.tb.*;

import com.tiviacz.travelersbackpack.attachment.AttachmentUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** See TbCompat: worn-backpack lookups also find the backpack in the Accessories slot (hotkeys, abilities, wrappers). */
@Mixin(value = AttachmentUtils.class, remap = false)
public abstract class AttachmentUtilsMixin {
    @Inject(method = "isWearingBackpack", at = @At("HEAD"), cancellable = true, require = 1)
    private static void infinium$accWearing(Player p, CallbackInfoReturnable<Boolean> cir) {
        if (!TbCompat.accessoryBackpack(p).isEmpty()) cir.setReturnValue(true);
    }
    @Inject(method = "getWearingBackpack", at = @At("HEAD"), cancellable = true, require = 1)
    private static void infinium$accBackpack(Player p, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack s = TbCompat.accessoryBackpack(p);
        if (!s.isEmpty()) cir.setReturnValue(s);
    }
}
