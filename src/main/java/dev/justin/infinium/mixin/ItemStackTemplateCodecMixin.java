package dev.justin.infinium.mixin;

import net.minecraft.world.item.ItemStackTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** 26.2 container contents (shulkers, bundles, Traveler's Backpack storage) save stacks as ItemStackTemplate, whose codec
 *  limits "count" to 1..99. Widen to 1..1024 so full-set stacks (up to 256) are never dropped from a save. */
@Mixin(ItemStackTemplate.class)
public abstract class ItemStackTemplateCodecMixin {
    @ModifyConstant(method = "lambda$static$0", constant = @Constant(intValue = 99), require = 1)
    private static int infinium$countRange(int old) { return 1024; }
}
