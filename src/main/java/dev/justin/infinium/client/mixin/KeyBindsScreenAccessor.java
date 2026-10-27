package dev.justin.infinium.client.mixin;

import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** M1: lets the "Bind key" HUD button scroll vanilla Key Binds to the Infinium category. */
@Mixin(KeyBindsScreen.class)
public interface KeyBindsScreenAccessor {
    @Accessor("keyBindsList") KeyBindsList infinium$keyBindsList();
}
