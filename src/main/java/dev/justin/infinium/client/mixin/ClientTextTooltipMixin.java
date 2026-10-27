package dev.justin.infinium.client.mixin;

import dev.justin.infinium.client.ClientTextTooltipAccessor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClientTextTooltip.class)
public abstract class ClientTextTooltipMixin implements ClientTextTooltipAccessor {
    @Shadow @Final private FormattedCharSequence text;
    @Override public FormattedCharSequence infinium$text() { return text; }
}
