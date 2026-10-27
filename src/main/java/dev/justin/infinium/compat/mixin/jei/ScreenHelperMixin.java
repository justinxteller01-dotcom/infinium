package dev.justin.infinium.compat.mixin.jei;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justin.infinium.compat.SpatialMargin;
import java.util.Optional;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.common.gui.GuiProperties;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Decouples JEI side panel anchors from Spatial GUI projection scaling so ingredient lists,
 * bookmarks, search bars, and submenus track screen edges correctly and don't glitch or overlap.
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.gui.helpers.ScreenHelper", remap = false)
public abstract class ScreenHelperMixin {
    @ModifyReturnValue(method = "getGuiProperties", at = @At("RETURN"), require = 1)
    private <T extends Screen> Optional<IGuiProperties> infinium$decoupleSpatial(Optional<IGuiProperties> opt, T screen) {
        if (!SpatialMargin.on() || opt.isEmpty()) return opt;
        IGuiProperties p = opt.get();
        // Return clean properties decoupled from any external projection offsets
        return Optional.of(new GuiProperties(p.screenClass(), p.guiLeft(), p.guiTop(), p.guiXSize(), p.guiYSize(), p.screenWidth(), p.screenHeight()));
    }
}
