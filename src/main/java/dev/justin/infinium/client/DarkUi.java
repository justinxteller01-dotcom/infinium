package dev.justin.infinium.client;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** pass2: built-in "Infinium Dark UI" resource pack (resourcepacks/dark_ui, made by gen_dark_ui.py), enabled by default. */
public final class DarkUi {
    public static void register() {
        FabricLoader.getInstance().getModContainer("infinium").ifPresent(mod ->
            ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath("infinium", "dark_ui"), mod,
                Component.literal("Infinium Dark UI"), PackActivationType.DEFAULT_ENABLED));
    }

    /** True while the pack is selected, so labels are only lightened over the dark textures. */
    public static boolean active() {
        for (String id : Minecraft.getInstance().getResourcePackRepository().getSelectedIds())
            if (id.endsWith("dark_ui")) return true;
        return false;
    }

    private DarkUi() {}
}
