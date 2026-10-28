package dev.justin.infinium.client.guide;

import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumStyle;
import dev.justin.infinium.StyleTemplates;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * Hard-coded stacks in vanilla GUI slot coordinates.
 * Crafting grid is 30,17 step 18, result 124,35.
 * Smithing slots are 8,48 / 26,48 / 44,48 / 98,48.
 * Those positions are the 1.21 smithing and crafting menus.
 * Verified with javap on 26.2: SmithingMenu 8/26/44/98 x 48, CraftingMenu grid 30,17 and result 124,35.
 */
public final class GuideStacks {
    public record Slot(int x, int y, ItemStack stack) {}

    /** station is "crafting" or "smithing". Slots are GUI-local pixels. */
    public record Layout(String station, List<Slot> slots) {}

    private GuideStacks() {}

    public static List<Layout> layouts(String recipe) {
        if (recipe == null || recipe.isEmpty()) return List.of();
        return switch (recipe) {
            case "crystal" -> List.of(crafting(new ItemLike[] {
                    Infinium.SPACETIME_FRAGMENTS, Infinium.SPACETIME_FRAGMENTS, Infinium.SPACETIME_FRAGMENTS,
                    Infinium.SPACETIME_FRAGMENTS, Items.AMETHYST_SHARD, Infinium.SPACETIME_FRAGMENTS,
                    Infinium.SPACETIME_FRAGMENTS, Infinium.SPACETIME_FRAGMENTS, Infinium.SPACETIME_FRAGMENTS
            }, of(Infinium.SPACETIME_CRYSTAL)));
            case "ingot" -> List.of(crafting(new ItemLike[] {
                    Items.ENDER_EYE, Items.OBSIDIAN, Items.DIAMOND_BLOCK,
                    Items.NETHERITE_INGOT, Infinium.SPACETIME_CRYSTAL, Items.NETHERITE_INGOT,
                    Items.WITHER_SKELETON_SKULL, Items.OBSIDIAN, Items.REDSTONE_BLOCK
            }, of(Infinium.INGOT)));
            case "tools" -> List.of(
                    tool(Items.NETHERITE_SWORD, Infinium.SWORD),
                    tool(Items.NETHERITE_PICKAXE, Infinium.PICKAXE),
                    tool(Items.NETHERITE_AXE, Infinium.AXE),
                    tool(Items.NETHERITE_SHOVEL, Infinium.SHOVEL),
                    tool(Items.NETHERITE_HOE, Infinium.HOE));
            case "finder" -> List.of(crafting(new ItemLike[] {
                    Items.BONE, Items.BONE, Items.BONE,
                    Items.BONE, Items.COMPASS, Items.BONE,
                    Items.BONE, Items.BONE, Items.BONE
            }, new ItemStack(dev.justin.infinium.DungeonFinder.ITEM)));
            case "templates" -> List.of(
                    smithing(template(InfiniumStyle.ASTRAL), of(Items.NETHERITE_HELMET), of(Infinium.INGOT), armor(Infinium.HELMET, InfiniumStyle.ASTRAL)),
                    smithing(template(InfiniumStyle.CYBERNETIC), of(Items.NETHERITE_HELMET), of(Infinium.INGOT), armor(Infinium.HELMET, InfiniumStyle.CYBERNETIC)),
                    smithing(template(InfiniumStyle.SINGULARITY), armor(Infinium.HELMET, InfiniumStyle.ASTRAL), of(Infinium.INGOT), armor(Infinium.HELMET, InfiniumStyle.SINGULARITY)),
                    smithing(template(InfiniumStyle.CYBERPSYCHO), armor(Infinium.HELMET, InfiniumStyle.CYBERNETIC), of(Infinium.INGOT), armor(Infinium.HELMET, InfiniumStyle.CYBERPSYCHO)));
            case "astral" -> List.of(smithing(
                    template(InfiniumStyle.ASTRAL), of(Items.NETHERITE_HELMET), of(Infinium.INGOT),
                    armor(Infinium.HELMET, InfiniumStyle.ASTRAL)));
            case "cybernetic" -> List.of(smithing(
                    template(InfiniumStyle.CYBERNETIC), of(Items.NETHERITE_CHESTPLATE), of(Infinium.INGOT),
                    armor(Infinium.CHESTPLATE, InfiniumStyle.CYBERNETIC)));
            case "singularity" -> List.of(smithing(
                    template(InfiniumStyle.SINGULARITY), armor(Infinium.HELMET, InfiniumStyle.ASTRAL), of(Infinium.INGOT),
                    armor(Infinium.HELMET, InfiniumStyle.SINGULARITY)));
            case "cyberpsycho" -> List.of(smithing(
                    template(InfiniumStyle.CYBERPSYCHO), armor(Infinium.CHESTPLATE, InfiniumStyle.CYBERNETIC), of(Infinium.INGOT),
                    armor(Infinium.CHESTPLATE, InfiniumStyle.CYBERPSYCHO)));
            case "infinity" -> List.of(smithing(
                    armor(Infinium.CHESTPLATE, InfiniumStyle.SINGULARITY),
                    armor(Infinium.CHESTPLATE, InfiniumStyle.CYBERPSYCHO),
                    of(Infinium.INGOT), of(Infinium.INF_CHESTPLATE)));
            case "dyes" -> dyes();
            case "rocket" -> List.of(smithing(
                    ItemStack.EMPTY, of(Items.FIREWORK_ROCKET), of(Infinium.INGOT), infinium("infinium_rocket")));
            default -> List.of();
        };
    }

    /** Welcome page. Book-local positions on the right page, not a station GUI. */
    public static List<Slot> scatter(int x, int y) {
        List<Slot> slots = new ArrayList<>();
        slots.add(new Slot(x, y, armor(Infinium.HELMET, InfiniumStyle.ASTRAL)));
        slots.add(new Slot(x + 18, y, armor(Infinium.CHESTPLATE, InfiniumStyle.CYBERNETIC)));
        slots.add(new Slot(x + 36, y, armor(Infinium.HELMET, InfiniumStyle.SINGULARITY)));
        slots.add(new Slot(x + 54, y, armor(Infinium.CHESTPLATE, InfiniumStyle.CYBERPSYCHO)));
        slots.add(new Slot(x + 18, y + 22, of(Infinium.INF_CHESTPLATE)));
        slots.add(new Slot(x, y + 48, of(Infinium.SWORD)));
        slots.add(new Slot(x + 18, y + 48, of(Infinium.PICKAXE)));
        slots.add(new Slot(x + 36, y + 48, of(Infinium.INGOT)));
        slots.add(new Slot(x + 54, y + 48, of(Infinium.SPACETIME_CRYSTAL)));
        slots.add(new Slot(x + 72, y + 48, template(InfiniumStyle.ASTRAL)));
        return slots;
    }

    private static Layout tool(ItemLike netherite, Item result) {
        return smithing(ItemStack.EMPTY, of(netherite), of(Infinium.INGOT), of(result));
    }

    private static List<Layout> dyes() {
        // Five static Cyber Psycho rows. "Elytra" is the chestplate (purple dye): dyes apply to the chestplate, not a separate elytra item.
        Item[] dyeItems = { dye("blue_dye"), dye("red_dye"), dye("green_dye"), dye("orange_dye"), dye("purple_dye") };
        ItemStack[] pieces = {
                armor(Infinium.HELMET, InfiniumStyle.CYBERPSYCHO),
                armor(Infinium.CHESTPLATE, InfiniumStyle.CYBERPSYCHO),
                armor(Infinium.LEGGINGS, InfiniumStyle.CYBERPSYCHO),
                armor(Infinium.BOOTS, InfiniumStyle.CYBERPSYCHO),
                armor(Infinium.CHESTPLATE, InfiniumStyle.CYBERPSYCHO)
        };
        List<Layout> out = new ArrayList<>();
        for (int i = 0; i < dyeItems.length; i++) {
            if (dyeItems[i] == null || dyeItems[i] == Items.AIR) continue;
            ItemStack res = pieces[i].copy();
            String path = BuiltInRegistries.ITEM.getKey(dyeItems[i]).getPath();
            net.minecraft.world.item.DyeColor dc = net.minecraft.world.item.DyeColor.byName(path.replace("_dye", ""), null);
            if (dc != null && dev.justin.infinium.GlowColor.GLOW_COLOR != null) res.set(dev.justin.infinium.GlowColor.GLOW_COLOR, dc);
            out.add(smithing(of(dyeItems[i]), pieces[i], ItemStack.EMPTY, res));
        }
        return out;
    }

    public static Item dye(String path) {
        // DefaultedRegistry.getValue(Identifier) returns the item (26.2 javadoc).
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(path));
    }

    private static ItemStack infinium(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("infinium", path)));
    }

    @SuppressWarnings("unchecked")
    public static ItemStack template(InfiniumStyle style) {
        for (var entry : ((java.util.Map<?, ?>) StyleTemplates.TEMPLATES).entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key instanceof InfiniumStyle s && s == style && value instanceof Item item) return new ItemStack(item);
            if (value instanceof InfiniumStyle s && s == style && key instanceof Item item) return new ItemStack(item);
            if (key instanceof Item item && StyleTemplates.styleOf(item) == style) return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack armor(Item item, InfiniumStyle style) {
        ItemStack stack = new ItemStack(item);
        stack.set(InfiniumStyle.STYLE, style);
        return stack;
    }

    private static ItemStack of(ItemLike item) {
        return new ItemStack(item);
    }

    private static Layout crafting(ItemLike[] cells, ItemStack result) {
        List<Slot> slots = new ArrayList<>(10);
        for (int i = 0; i < 9; i++) {
            slots.add(new Slot(30 + (i % 3) * 18, 17 + (i / 3) * 18, of(cells[i])));
        }
        slots.add(new Slot(124, 35, result));
        return new Layout("crafting", slots);
    }

    private static Layout smithing(ItemStack template, ItemStack base, ItemStack addition, ItemStack result) {
        List<Slot> slots = new ArrayList<>(4);
        slots.add(new Slot(8, 48, template));
        slots.add(new Slot(26, 48, base));
        slots.add(new Slot(44, 48, addition));
        slots.add(new Slot(98, 48, result));
        return new Layout("smithing", slots);
    }
}
