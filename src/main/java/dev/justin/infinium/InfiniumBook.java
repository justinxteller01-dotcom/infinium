package dev.justin.infinium;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Called at the end of {@code Infinium.onInitialize} (see {@code InfiniumInitMixin}).
 * Registries are still open until every mod initializer returns.
 */
public final class InfiniumBook {
    public static Item GUIDE;

    private InfiniumBook() {}

    public static void bootstrap() {
        if (GUIDE != null) return;
        GUIDE = Infinium.reg("infinium_guide", InfiniumGuide::createItem, new Item.Properties().stacksTo(1).fireResistant().rarity(net.minecraft.world.item.Rarity.UNCOMMON));
        ((net.fabricmc.fabric.api.event.registry.FabricRegistry) BuiltInRegistries.ITEM).addAlias(Infinium.id("informational_crystal"), Infinium.id("infinium_guide"));
        GuideGiven.registerEvents();
        // 26.2 FabricCreativeModeTabOutput.accept requires TabVisibility. There is no one-arg accept.
        CreativeModeTabEvents.modifyOutputEvent(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, net.minecraft.resources.Identifier.withDefaultNamespace("ingredients")))
                .register(output -> output.accept(GUIDE));
    }
}
