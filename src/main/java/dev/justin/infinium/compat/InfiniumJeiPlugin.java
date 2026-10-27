package dev.justin.infinium.compat;

import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumStyle;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/** JEI: Infinium armor is split by infinium:style only (unstyled + 4 styles); dye colour is NOT a subtype, so each
 *  piece/style appears once. Loaded only via the jei_mod_plugin entrypoint, so JEI stays optional. */
@JeiPlugin
public class InfiniumJeiPlugin implements IModPlugin {
    @Override public Identifier getPluginUid() { return Infinium.id("jei_plugin"); }

    /** Effect icons sit above the inventory now. Tell JEI to avoid that strip instead of the old right-hand column.
     *  compat jei.InventoryEffectRendererMixin empties JEI's own side-column measurement (getEffectAreas). */
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        @SuppressWarnings({"unchecked","rawtypes"}) Class<AbstractContainerScreen<?>> acs = (Class) AbstractContainerScreen.class;
        registration.addGuiContainerHandler(acs, new IGuiContainerHandler<AbstractContainerScreen<?>>() {
            @Override public List<Rect2i> getGuiExtraAreas(AbstractContainerScreen<?> screen) {
                // R5b: only the screens that show effects, only when there is a visible effect, sized like
                // EffectsInInventoryMixin's row (14 px icons, 2 px gap, centered on the 176 px panel, 1 px frame).
                if (!(screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
                        || screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)) return List.of();
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player == null) return List.of();
                int n = 0;
                for (var e : mc.player.getActiveEffects()) if (e.showIcon()) n++;
                if (n == 0) return List.of();
                int total = n * 16 - 2;
                int x = Math.max(3, screen.width - total - 7);
                int y = 5;
                return List.of(new Rect2i(x, y, total + 2, 16));
            }
        });
    }
    @Override public void registerItemSubtypes(ISubtypeRegistration r) {
        for (Item it : new Item[]{Infinium.HELMET, Infinium.CHESTPLATE, Infinium.LEGGINGS, Infinium.BOOTS})
            r.registerFromDataComponentTypes(it, InfiniumStyle.STYLE);
    }
    @Override public void registerRecipes(mezz.jei.api.registration.IRecipeRegistration r) {
        java.util.List<net.minecraft.world.item.ItemStack> all = new java.util.ArrayList<>(Infinium.styledArmor());
        for (Item it : new Item[]{Infinium.HELMET, Infinium.CHESTPLATE, Infinium.LEGGINGS, Infinium.BOOTS, Infinium.SWORD, Infinium.PICKAXE, Infinium.AXE, Infinium.SHOVEL, Infinium.HOE})
            all.add(new net.minecraft.world.item.ItemStack(it));
        for (net.minecraft.world.item.ItemStack s : all)
            r.addItemStackInfo(s, dev.justin.infinium.Info.details(s).toArray(new net.minecraft.network.chat.Component[0]));
        for (Item it : new Item[]{Infinium.STRANGE_FRAGMENTS, Infinium.SPACETIME_CRYSTAL}) { var s = new net.minecraft.world.item.ItemStack(it);
            r.addItemStackInfo(s, dev.justin.infinium.Info.details(s).toArray(new net.minecraft.network.chat.Component[0])); }
        for (Item it : dev.justin.infinium.StyleTemplates.TEMPLATES.values()) { var s = new net.minecraft.world.item.ItemStack(it);
            r.addItemStackInfo(s, dev.justin.infinium.Info.details(s).toArray(new net.minecraft.network.chat.Component[0])); }
        r.addItemStackInfo(new net.minecraft.world.item.ItemStack(dev.justin.infinium.DungeonFinder.ITEM), dev.justin.infinium.DungeonFinder.info().toArray(new net.minecraft.network.chat.Component[0]));
    }
    @Override public void registerExtraIngredients(IExtraIngredientRegistration r) { r.addExtraItemStacks(Infinium.styledArmor()); }
}
