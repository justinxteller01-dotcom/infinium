package dev.justin.infinium;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;

/**
 * Keep-chance, server only. Food ({@code Consumable}) and fireworks still use {@link #roll} with no block filter.
 * A placed block is kept only when {@link #blockAllowed} is true and {@link #roll} succeeds.
 * Exclusions always beat an allow tag. Creative tabs are not consulted (empty on a dedicated server).
 */
public final class NoConsume {
    private static TagKey<Item> tag(String ns, String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ns, path));
    }

    private static final TagKey<Item> C_ORES = tag("c", "ores");
    private static final TagKey<Item> C_STORAGE = tag("c", "storage_blocks");
    private static final TagKey<Item> C_STONES = tag("c", "stones");
    private static final TagKey<Item> C_COBBLE = tag("c", "cobblestones");
    private static final TagKey<Item> C_SANDS = tag("c", "sands");
    private static final TagKey<Item> C_GRAVELS = tag("c", "gravels");
    private static final TagKey<Item> C_GLASS = tag("c", "glass_blocks");
    private static final TagKey<Item> C_PANES = tag("c", "glass_panes");
    private static final TagKey<Item> C_DYED = tag("c", "dyed");
    private static final TagKey<Item> C_CONCRETE = tag("c", "concretes");
    private static final TagKey<Item> C_POWDER = tag("c", "concrete_powders");
    private static final TagKey<Item> C_GLAZED = tag("c", "glazed_terracottas");
    private static final TagKey<Item> C_SEEDS = tag("c", "seeds");
    private static final TagKey<Item> C_STRIPPED_LOGS = tag("c", "stripped_logs");
    private static final TagKey<Item> C_NATURAL_LOGS = tag("c", "natural_logs");
    private static final TagKey<Item> C_CHESTS = tag("c", "chests");
    private static final TagKey<Item> C_BARRELS = tag("c", "barrels");
    private static final TagKey<Item> C_SHULKERS = tag("c", "shulker_boxes");
    private static final TagKey<Item> MC_LOGS = tag("minecraft", "logs");
    private static final TagKey<Item> MC_PLANKS = tag("minecraft", "planks");
    private static final TagKey<Item> MC_LEAVES = tag("minecraft", "leaves");
    private static final TagKey<Item> MC_SAPLINGS = tag("minecraft", "saplings");
    private static final TagKey<Item> MC_WOOL = tag("minecraft", "wool");
    private static final TagKey<Item> MC_CARPETS = tag("minecraft", "wool_carpets");
    private static final TagKey<Item> MC_TERRACOTTA = tag("minecraft", "terracotta");
    private static final TagKey<Item> MC_CANDLES = tag("minecraft", "candles");
    private static final TagKey<Item> MC_STONE_BRICKS = tag("minecraft", "stone_bricks");
    private static final TagKey<Item> MC_WALLS = tag("minecraft", "walls");
    private static final TagKey<Item> MC_STAIRS = tag("minecraft", "stairs");
    private static final TagKey<Item> MC_SLABS = tag("minecraft", "slabs");
    private static final TagKey<Item> MC_WOOD_STAIRS = tag("minecraft", "wooden_stairs");
    private static final TagKey<Item> MC_WOOD_SLABS = tag("minecraft", "wooden_slabs");
    private static final TagKey<Item> MC_FENCES = tag("minecraft", "fences");
    private static final TagKey<Item> MC_WOOD_FENCES = tag("minecraft", "wooden_fences");
    private static final TagKey<Item> MC_GATES = tag("minecraft", "fence_gates");
    private static final TagKey<Item> MC_DOORS = tag("minecraft", "doors");
    private static final TagKey<Item> MC_TRAPDOORS = tag("minecraft", "trapdoors");
    private static final TagKey<Item> MC_SAND = tag("minecraft", "sand");
    private static final TagKey<Item> MC_DIRT = tag("minecraft", "dirt");
    private static final TagKey<Item> MC_SEEDS = tag("minecraft", "villager_plantable_seeds");
    private static final TagKey<Item> MC_SKULLS = tag("minecraft", "skulls");
    private static final TagKey<Item> MC_COAL_ORES = tag("minecraft", "coal_ores");
    private static final TagKey<Item> MC_COPPER_ORES = tag("minecraft", "copper_ores");
    private static final TagKey<Item> MC_DIAMOND_ORES = tag("minecraft", "diamond_ores");
    private static final TagKey<Item> MC_EMERALD_ORES = tag("minecraft", "emerald_ores");
    private static final TagKey<Item> MC_GOLD_ORES = tag("minecraft", "gold_ores");
    private static final TagKey<Item> MC_IRON_ORES = tag("minecraft", "iron_ores");
    private static final TagKey<Item> MC_LAPIS_ORES = tag("minecraft", "lapis_ores");
    private static final TagKey<Item> MC_REDSTONE_ORES = tag("minecraft", "redstone_ores");
    // Pass2: Natural / Building tab coverage that the tags above miss (flowers, sandstone, netherrack, end stone, obsidian).
    private static final TagKey<Item> MC_FLOWERS = tag("minecraft", "flowers");
    private static final TagKey<Item> MC_SMALL_FLOWERS = tag("minecraft", "small_flowers");
    private static final TagKey<Item> C_SANDSTONE = tag("c", "sandstone_blocks");
    private static final TagKey<Item> C_NETHERRACKS = tag("c", "netherracks");
    private static final TagKey<Item> C_END_STONES = tag("c", "end_stones");
    private static final TagKey<Item> C_OBSIDIANS = tag("c", "obsidians");
    /** Vanilla building/natural block path words. Checked only after every exclusion. */
    private static final String[] VANILLA_WORDS = {"brick", "stone", "prismarine", "purpur", "deepslate", "blackstone", "basalt",
        "tuff", "andesite", "diorite", "granite", "calcite", "mud", "clay", "snow", "ice", "moss", "mushroom", "cactus",
        "sugar_cane", "kelp", "bamboo", "vine", "lily_pad", "fern", "grass", "netherrack", "nylium", "wart_block", "shroomlight",
        "coral", "dripstone", "magma", "soul_s", "froglight", "glass", "sea_pickle", "azalea", "dripleaf", "roots", "hanging_moss",
        "pale_", "spore_blossom", "glow_lichen", "podzol", "mycelium", "farmland", "path", "wood", "stem", "hyphae", "resin_brick"};
    /** Functional / redstone blocks whose path happens to contain a word above. */
    private static final String[] VANILLA_NOT = {"button", "pressure_plate", "redstone", "lodestone", "grindstone", "stonecutter",
        "glowstone_dust", "sign", "boat", "raft", "shelf", "lectern", "loom", "table", "lantern", "torch", "campfire", "chain"};

    public static boolean excluded(ItemStack s) {
        return s.getItem() instanceof BucketItem || s.getItem() instanceof PotionItem || s.has(DataComponents.USE_REMAINDER)
            || s.is(Items.ENCHANTED_GOLDEN_APPLE) || s.is(Items.GOLDEN_APPLE)
            || s.is(Items.MILK_BUCKET) || s.is(Items.HONEY_BOTTLE) || s.is(Items.OMINOUS_BOTTLE) || s.is(Items.MUSHROOM_STEW)
            || s.is(Items.RABBIT_STEW) || s.is(Items.BEETROOT_SOUP) || s.is(Items.SUSPICIOUS_STEW) || s.is(Items.POWDER_SNOW_BUCKET)
            || s.is(Items.EXPERIENCE_BOTTLE) || s.is(Items.ENDER_PEARL);
    }

    /**
     * Block-save filter for {@code BlockItem.place} only. Exclusions beat allows.
     * Not an EMC rule and not a creative-tab lookup.
     */
    public static boolean blockAllowed(ItemStack s) {
        if (!(s.getItem() instanceof BlockItem bi)) return false;
        Block block = bi.getBlock();
        if (block instanceof EntityBlock || block.defaultBlockState().hasBlockEntity()) return false;
        if (block.defaultBlockState().is(BlockTags.BEACON_BASE_BLOCKS)) return false;
        if (hardExcluded(s)) return false;
        return allowed(s);
    }

    private static boolean hardExcluded(ItemStack s) {
        if (s.is(C_ORES) || s.is(MC_COAL_ORES) || s.is(MC_COPPER_ORES) || s.is(MC_DIAMOND_ORES) || s.is(MC_EMERALD_ORES)
            || s.is(MC_GOLD_ORES) || s.is(MC_IRON_ORES) || s.is(MC_LAPIS_ORES) || s.is(MC_REDSTONE_ORES) || s.is(MC_SKULLS)) return true;
        if (s.is(C_CHESTS) || s.is(C_BARRELS) || s.is(C_SHULKERS)) return true;
        if (s.is(C_STORAGE) && !storageAllowed(s)) return true;
        Identifier id = BuiltInRegistries.ITEM.getKey(s.getItem());
        String path = id.getPath();
        if (mineralPath(path)) return true;
        if ("rechiseled".equals(id.getNamespace()) && rechiseledMineral(path)) return true;
        return s.is(Items.AMETHYST_BLOCK) || s.is(Items.BUDDING_AMETHYST) || s.is(Items.RESPAWN_ANCHOR)
            || s.is(Items.DRAGON_EGG) || s.is(Items.BEACON) || s.is(Items.SLIME_BLOCK) || s.is(Items.HONEY_BLOCK)
            || s.is(Items.TNT) || s.is(Items.REDSTONE_BLOCK) || s.is(Items.ANCIENT_DEBRIS)
            || s.is(Items.SCULK) || s.is(Items.SCULK_CATALYST) || s.is(Items.SCULK_SENSOR)
            || s.is(Items.CALIBRATED_SCULK_SENSOR) || s.is(Items.SCULK_SHRIEKER) || s.is(Items.SCULK_VEIN);
    }

    /** The three #c:storage_blocks the spec names as allowed. Every other storage block is a mineral or a valuable. */
    private static boolean storageAllowed(ItemStack s) {
        return s.is(Items.BONE_BLOCK) || s.is(Items.DRIED_KELP_BLOCK) || s.is(Items.HAY_BLOCK);
    }

    /** Vanilla mineral / metal block paths. Quartz blocks are not in this list (quartz ore is already in #c:ores). */
    private static boolean mineralPath(String path) {
        return path.contains("copper") || path.contains("ancient_debris") || path.contains("netherite")
            || path.contains("raw_iron") || path.contains("raw_gold") || path.contains("raw_copper")
            || path.equals("iron_block") || path.equals("gold_block") || path.equals("diamond_block")
            || path.equals("emerald_block") || path.equals("lapis_block") || path.equals("coal_block")
            || path.equals("redstone_block") || path.equals("amethyst_block") || path.equals("budding_amethyst");
    }

    private static boolean rechiseledMineral(String path) {
        String[] bases = {"iron_block", "gold_block", "diamond_block", "emerald_block", "lapis_block",
            "redstone_block", "coal_block", "netherite_block", "copper_block", "amethyst_block", "raw_iron",
            "raw_gold", "raw_copper"};
        for (String b : bases) if (path.startsWith(b)) return true;
        return false;
    }

    private static boolean allowed(ItemStack s) {
        if (s.is(Items.GLOWSTONE) || s.is(Items.MELON) || s.is(Items.PUMPKIN)
            || s.is(Items.CARVED_PUMPKIN) || s.is(Items.JACK_O_LANTERN)   // pass2: allowed again (golem-part exclusion dropped)
            || s.is(Items.HAY_BLOCK) || s.is(Items.DRIED_KELP_BLOCK) || s.is(Items.BONE_BLOCK)) return true;
        if (quartzBlock(s)) return true;
        if (s.is(MC_LOGS) || s.is(MC_PLANKS) || s.is(MC_LEAVES) || s.is(MC_SAPLINGS) || s.is(MC_WOOL) || s.is(MC_CARPETS)
            || s.is(MC_TERRACOTTA) || s.is(MC_CANDLES) || s.is(MC_STONE_BRICKS) || s.is(MC_WALLS) || s.is(MC_STAIRS)
            || s.is(MC_SLABS) || s.is(MC_WOOD_STAIRS) || s.is(MC_WOOD_SLABS) || s.is(MC_FENCES) || s.is(MC_WOOD_FENCES)
            || s.is(MC_GATES) || s.is(MC_DOORS) || s.is(MC_TRAPDOORS) || s.is(MC_SAND) || s.is(MC_DIRT) || s.is(MC_SEEDS)) return true;
        if (s.is(C_STONES) || s.is(C_COBBLE) || s.is(C_SANDS) || s.is(C_GRAVELS) || s.is(C_GLASS) || s.is(C_PANES)
            || s.is(C_DYED) || s.is(C_CONCRETE) || s.is(C_POWDER) || s.is(C_GLAZED) || s.is(C_SEEDS)
            || s.is(C_STRIPPED_LOGS) || s.is(C_NATURAL_LOGS)) return true;
        if (s.is(MC_FLOWERS) || s.is(MC_SMALL_FLOWERS) || s.is(C_SANDSTONE) || s.is(C_NETHERRACKS) || s.is(C_END_STONES)
            || s.is(C_OBSIDIANS)) return true;
        Identifier id = BuiltInRegistries.ITEM.getKey(s.getItem());
        if ("minecraft".equals(id.getNamespace())) {
            String path = id.getPath();
            for (String no : VANILLA_NOT) if (path.contains(no)) return false;
            for (String w : VANILLA_WORDS) if (path.contains(w)) return true;
            return false;
        }
        return "rechiseled".equals(id.getNamespace());
    }

    private static boolean quartzBlock(ItemStack s) {
        if (s.is(C_ORES)) return false;
        String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        return path.contains("quartz") && !path.contains("ore");
    }

    /** true = skip the consume (the item is kept). Resyncs the client once when it triggers. */
    public static boolean roll(LivingEntity e, ItemStack s) {
        if (!(e instanceof ServerPlayer sp) || sp.hasInfiniteMaterials() || excluded(s) || SetBonuses.keepChance(sp) <= 0) return false;
        if (sp.getRandom().nextFloat() >= SetBonuses.keepChance(sp)) return false;
        sp.inventoryMenu.sendAllDataToRemote();
        sp.containerMenu.sendAllDataToRemote();
        return true;
    }
    private NoConsume() {}
}
