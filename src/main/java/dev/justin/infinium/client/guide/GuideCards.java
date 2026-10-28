package dev.justin.infinium.client.guide;

import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumStyle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * Right-page card stacks, modelled on the Crystal Matrix page (drawMatrixShowcase / drawMilestoneCard):
 * bordered dark-purple rectangles stacked top to bottom, each with a small title, short plain lines,
 * and optional icon, fill bar, perk pips, or an embedded recipe. Rendered by InfiniumGuideScreen#drawCardStack.
 * Purple / pink / black palette only.
 */
public final class GuideCards {
    public static final int PURPLE = 0xFFC45AD4, VIOLET = 0xFFA14AD0, PINK = 0xFFFF00AA, ROSE = 0xFFD24A8A, HOT = 0xFFFF44C1;

    /** bar: 0..1 fill, or -1 for none. pips: total pips (0 = none), pipsOn: how many lit. recipe: GuideStacks key (cycles if several). */
    public record Card(String title, int titleCol, List<String> lines, ItemStack icon, float bar, int pips, int pipsOn, String recipe) {
        boolean isRecipe() { return recipe != null && !recipe.isEmpty(); }
    }

    private GuideCards() {}

    static Card info(String title, int col, ItemLike icon, String... lines) {
        return new Card(title, col, List.of(lines), icon == null ? ItemStack.EMPTY : new ItemStack(icon), -1f, 0, 0, null);
    }
    static Card info(String title, int col, ItemStack icon, String... lines) {
        return new Card(title, col, List.of(lines), icon, -1f, 0, 0, null);
    }
    static Card bar(String title, int col, ItemStack icon, float bar, String... lines) {
        return new Card(title, col, List.of(lines), icon, bar, 0, 0, null);
    }
    static Card pips(String title, int col, ItemStack icon, int pips, int on, String... lines) {
        return new Card(title, col, List.of(lines), icon, -1f, pips, on, null);
    }
    static Card recipe(String key) {
        return new Card("", PURPLE, List.of(), ItemStack.EMPTY, -1f, 0, 0, key);
    }

    /** Empty list = page keeps its old right side. */
    public static List<Card> forPage(String title, String art, String recipe, String note) {
        if (Infinium.HELMET == null) return List.of();
        List<Card> c = new ArrayList<>();
        switch (title) {
            case "Fragments & Crystals" -> {
                c.add(recipe("crystal"));
                c.add(info("Craft a crystal", PINK, Infinium.SPACETIME_FRAGMENTS, "8 fragments around 1 amethyst shard"));
                c.add(info("Endless energy", PURPLE, Infinium.SPACETIME_CRYSTAL, "Small, steady power that never runs out"));
                c.add(info("Power your armor", VIOLET, Infinium.HELMET, "Insert into a full set to unlock perks"));
            }
            case "Roguelike Dungeons" -> {
                c.add(info("Where fragments hide", PINK, Items.CHEST, "World chests. Rarer chests, better odds"));
                c.add(recipe("finder"));
                c.add(info("Dungeon Finder", PURPLE, dev.justin.infinium.DungeonFinder.ITEM, "Points to the nearest dungeon"));
                c.add(new Card("Beam colors (dungeon size)", VIOLET, List.of(), ItemStack.EMPTY, -1f, 0, 0, null));
                c.add(bar("Green", ROSE, ItemStack.EMPTY, 1 / 3f, "Normal"));
                c.add(bar("Yellow", ROSE, ItemStack.EMPTY, 2 / 3f, "Large"));
                c.add(bar("Red", PINK, ItemStack.EMPTY, 1f, "Extra large"));
            }
            case "Infinium Ingot" -> {
                c.add(recipe("ingot"));
                c.add(info("Center", PINK, Infinium.SPACETIME_CRYSTAL, "1 spacetime crystal"));
                c.add(info("Left and right", PURPLE, Items.NETHERITE_INGOT, "Netherite ingots"));
                c.add(info("Top and bottom", PURPLE, Items.OBSIDIAN, "Obsidian"));
                c.add(info("Corners (any order)", VIOLET, Items.WITHER_SKELETON_SKULL, "Eye of Ender, Diamond Block,", "Wither Skull, Redstone Block"));
            }
            case "Upgrade Templates" -> {
                c.add(recipe("templates"));
                c.add(pips("Astral", PURPLE, GuideStacks.template(InfiniumStyle.ASTRAL), 2, 1, "Common. Netherite to Tier 1"));
                c.add(pips("Cybernetic", ROSE, GuideStacks.template(InfiniumStyle.CYBERNETIC), 2, 1, "Common. Netherite to Tier 1"));
                c.add(pips("Singularity", VIOLET, GuideStacks.template(InfiniumStyle.SINGULARITY), 2, 2, "Rare. Astral to Tier 2"));
                c.add(pips("Cyber Psycho", PINK, GuideStacks.template(InfiniumStyle.CYBERPSYCHO), 2, 2, "Rare. Cybernetic to Tier 2"));
            }
            case "Tools" -> {
                c.add(recipe("tools"));
                c.add(info("Sword", PINK, Infinium.SWORD, "12 damage, bleed. Full swing fires a slash"));
                c.add(info("Pickaxe", PURPLE, Infinium.PICKAXE, "Fortune IV or Silk Touch"));
                c.add(info("Axe", PURPLE, Infinium.AXE, "Logs drop the log type you pick"));
                c.add(info("Shovel", VIOLET, Infinium.SHOVEL, "1x1, 3x3, or 3x1 digging"));
                c.add(info("Hoe", VIOLET, Infinium.HOE, "Farmland stays wet, lit, untrampled"));
                c.add(info("Change modes", HOT, (ItemLike) null, "Sneak + Right Click on air"));
            }
            case "Dye It Your Way" -> {
                c.add(recipe("dyes"));
                c.add(info("Template slot", PINK, Items.PURPLE_DYE, "Any dye"));
                c.add(info("Base slot", PURPLE, Infinium.CHESTPLATE, "Your armor or tool"));
                c.add(info("No ingot needed", VIOLET, Infinium.INGOT, "Re-dye any time, as often as you like"));
            }
            case "Settings" -> {
                c.add(pips("Built-in wings", PINK, Infinium.CHESTPLATE == null ? ItemStack.EMPTY : new ItemStack(Infinium.CHESTPLATE), 2, 1,
                        "On by default. Shift + Right Click", "on air (empty hand) or set a hotkey"));
                c.add(info("Purple gear icon", PURPLE, Items.CRAFTING_TABLE, "In your inventory, right of the", "recipe book. Opens to your set"));
                c.add(info("Shows up when", VIOLET, Infinium.HELMET, "You get your first Infinium piece"));
                c.add(info("Customize", ROSE, Infinium.SPACETIME_CRYSTAL, "Perks, particles, lighting, sounds"));
                c.add(info("World Effects tab", HOT, Items.NETHER_STAR, "Shooting stars, sky ripples,", "underwater shimmer"));
            }
            default -> armor(c, art, note);
        }
        return c;
    }

    private static void armor(List<Card> c, String art, String note) {
        int tier; float taken; int col; boolean inf = false; InfiniumStyle style = null; String sub;
        switch (art) {
            case "astral" -> { tier = 1; taken = -1f; col = PURPLE; style = InfiniumStyle.ASTRAL; sub = "Tougher than netherite"; }
            case "cybernetic" -> { tier = 1; taken = 0.667f; col = ROSE; style = InfiniumStyle.CYBERNETIC; sub = "Damage taken: 66.7% of netherite"; }
            case "singularity" -> { tier = 2; taken = 0.571f; col = VIOLET; style = InfiniumStyle.SINGULARITY; sub = "Damage taken: 57.1% of netherite"; }
            case "cyberpsycho" -> { tier = 2; taken = 0.571f; col = PINK; style = InfiniumStyle.CYBERPSYCHO; sub = "Damage taken: 57.1% of netherite"; }
            case "infinity" -> { tier = 3; taken = 0.5f; col = HOT; inf = true; sub = "Damage taken: 50% of netherite"; }
            default -> { return; }
        }
        Item[] pieces = inf ? new Item[] {Infinium.INF_HELMET, Infinium.INF_CHESTPLATE, Infinium.INF_LEGGINGS, Infinium.INF_BOOTS}
                : new Item[] {Infinium.HELMET, Infinium.CHESTPLATE, Infinium.LEGGINGS, Infinium.BOOTS};
        ItemStack chest = inf ? new ItemStack(pieces[1]) : GuideStacks.armor(pieces[1], style);
        c.add(new Card("Tier " + tier + " armor", col, List.of(sub), chest, taken, 0, 0, null));
        String[] lines = note.split("\n");
        for (int i = 0; i < Math.min(4, lines.length); i++) {
            String ln = lines[i];
            int k = ln.indexOf(':');
            String head = k > 0 ? ln.substring(0, k) : "";
            String body = k > 0 ? ln.substring(k + 1).trim() : ln;
            ItemStack icon = inf ? new ItemStack(pieces[i]) : GuideStacks.armor(pieces[i], style);
            c.add(info(head, i % 2 == 0 ? col : PURPLE, icon, body));
        }
        c.add(recipe(art));
    }
}
