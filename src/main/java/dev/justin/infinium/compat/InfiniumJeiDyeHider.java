package dev.justin.infinium.compat;

import dev.justin.infinium.Infinium;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;

/**
 * Second JEI plugin so {@code InfiniumJeiPlugin} stays untouched.
 * Add this class to the {@code jei_mod_plugin} entrypoint (gen_book.py does that).
 *
 * Hides every {@code infinium:glow_dye_*} smithing recipe (armor, infinity, tools, dye+upgrade)
 * and any infinium stack that carries {@code infinium:glow_color}, so dyed variants are not
 * their own ingredient entries. The recipe JSON is unchanged; a smithing table still recolours.
 *
 * JEI 30.42 / 26.2 (verified on the 26.2 branch of JustEnoughItems):
 * {@code IRecipeHolderType<SmithingRecipe>} extends {@code IRecipeType<RecipeHolder<SmithingRecipe>>},
 * {@code hideRecipes(IRecipeType, Collection)}, {@code createRecipeLookup(type).get()} is a Stream.
 */
@JeiPlugin
public final class InfiniumJeiDyeHider implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Infinium.id("jei_dye_hider");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        List<RecipeHolder<SmithingRecipe>> hide = runtime.getRecipeManager()
                .createRecipeLookup(RecipeTypes.SMITHING)
                .includeHidden()
                .get()
                .filter(holder -> {
                    // UNVERIFIED: javap RecipeHolder id() — spec and 1.21.2+ ResourceKey use identifier().
                    Identifier id = holder.id().identifier();
                    return "infinium".equals(id.getNamespace()) && id.getPath().startsWith("glow_dye_");
                })
                .toList();
        if (!hide.isEmpty()) runtime.getRecipeManager().hideRecipes(RecipeTypes.SMITHING, hide);

        List<ItemStack> dyed = new ArrayList<>();
        for (ItemStack stack : runtime.getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK)) {
            if (isDyedInfinium(stack)) dyed.add(stack);
        }
        if (!dyed.isEmpty()) {
            runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, dyed);
        }
    }

    private static boolean isDyedInfinium(ItemStack stack) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !"infinium".equals(itemId.getNamespace())) return false;
        for (DataComponentType<?> type : stack.getComponents().keySet()) {
            Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (id != null && "infinium".equals(id.getNamespace()) && "glow_color".equals(id.getPath())) return true;
        }
        return false;
    }
}
