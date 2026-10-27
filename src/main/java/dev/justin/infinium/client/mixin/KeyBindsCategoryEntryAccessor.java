package dev.justin.infinium.client.mixin;

import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** M1: reads a category header's label so we can find the Infinium section. */
@Mixin(KeyBindsList.CategoryEntry.class)
public interface KeyBindsCategoryEntryAccessor {
    @Accessor("categoryName") FocusableTextWidget infinium$categoryName();
}
