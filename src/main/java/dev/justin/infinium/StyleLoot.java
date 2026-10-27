package dev.justin.infinium;

import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * Infinium chest loot. Natural world chests (R6: MODIFY_DROPS in loot.NaturalRefill, deterministic per player): per table tier one template pool (chance pT, weights
 * Astral 3, Cybernetic 3, Singularity 1, CyberPsycho 1) and one Spacetime Fragments pool (chance pF, fMin-fMax). No crystals.
 * Tiers: LOW (villages etc.), MID, HIGH (fortress, bastion, end city, ancient city, stronghold, mansion, temples, buried treasure,
 * shipwreck treasure, trial vaults). R5c: natural chests are deliberately much poorer than Roguelike Dungeons.
 * Roguelike Dungeons: per-dungeon budget, added at roll time via MODIFY_DROPS (DungeonLoot) so Lootr per-player rolls get it.
 * Custom dungeon levels: config/infinium_dungeon_loot.json. Balance table: design_update/LOOT_R5C.md.
 */
public final class StyleLoot {
    public record Tier(float pT, float pF, int fMin, int fMax, float pC) {}
    public static final Tier LOW = new Tier(0.0005F, 0.015F, 1, 1, 0.0F), MID = new Tier(0.003F, 0.06F, 1, 2, 0.0F),
                             HIGH = new Tier(0.01F, 0.12F, 1, 2, 0.0F);   // 2026-10-22: fragment chance = original (0.01/0.04/0.08) x1.5; templates unchanged
    public static final int W_ASTRAL = 3, W_CYB = 3, W_SING = 1, W_PSYCHO = 1;
    public static final Map<String, Tier> TABLES = new LinkedHashMap<>();
    /** Other mods' chests/* tables (structure mods): MID. Roguelike tables: none (per-dungeon placement, RoguelikeCompat). */
    static {
        for (String t : new String[]{"village/village_weaponsmith", "village/village_armorer", "village/village_toolsmith", "village/village_temple",
                "village/village_cartographer", "village/village_mason", "village/village_plains_house", "village/village_desert_house",
                "village/village_savanna_house", "village/village_snowy_house", "village/village_taiga_house", "spawn_bonus_chest"}) c(t, LOW);
        for (String t : new String[]{"simple_dungeon", "abandoned_mineshaft", "ruined_portal", "igloo_chest", "pillager_outpost",
                "underwater_ruin_big", "underwater_ruin_small", "shipwreck_supply", "shipwreck_map", "bastion_hoglin_stable",
                "trial_chambers/corridor", "trial_chambers/supply"}) c(t, MID);
        for (String t : new String[]{"nether_bridge", "bastion_treasure", "bastion_other", "bastion_bridge", "end_city_treasure",
                "ancient_city", "ancient_city_ice_box", "stronghold_corridor", "stronghold_crossing", "stronghold_library",
                "woodland_mansion", "desert_pyramid", "jungle_temple", "buried_treasure", "shipwreck_treasure",
                "trial_chambers/reward", "trial_chambers/reward_rare", "trial_chambers/reward_ominous", "trial_chambers/reward_ominous_rare"}) c(t, HIGH);
    }
    private static void c(String path, Tier t) { TABLES.put("minecraft:chests/" + path, t); }

    public static Tier tierFor(Identifier id) {
        Tier t = TABLES.get(id.toString());
        if (t != null) return t;
        Tier d = DungeonLevels.tierFor(id);                      // custom dungeon levels (config)
        if (d != null) return d;
        if (id.getNamespace().equals("minecraft") || id.getNamespace().equals(Infinium.MODID) || id.getNamespace().equals("roguelike")) return null;
        String ns = id.getNamespace(), path = id.getPath();
        if (!path.startsWith("chests/") && !path.contains("chest")) return null;
        return modTier(ns, path);
    }
    /** Structure mods on the server (Lootr per-player chests use these same tables, so MODIFY covers them too). */
    static Tier modTier(String ns, String path) {
        String p = path.toLowerCase(java.util.Locale.ROOT);
        if (p.contains("village") || p.contains("town") || p.contains("house") || p.contains("farm") || p.contains("bonus")) return LOW;
        if (ns.equals("betterend") || ns.equals("better_end") || ns.equals("end_villager_outpost") || ns.equals("formationsnether")) return HIGH;
        for (String k : new String[]{"mansion", "fortress", "nether_bridge", "bastion", "end_city", "city", "stronghold", "temple",
                "pyramid", "treasure", "ancient", "vault", "citadel", "castle", "keep"}) if (p.contains(k)) return HIGH;
        return MID;   // repurposed_structures mineshafts/dungeons/outposts/ruins, t_and_t towers, formations, other structure mods
    }
    /** Custom dungeon level L >= 2 (config "tables"/"patterns", other dungeon mods): per chest, from about MID (L=2) to about
     *  2x HIGH (L>=8). No crystals. Roguelike Dungeons do NOT use this (per-dungeon budget, DungeonLoot). */
    public static Tier forLevel(int level) {
        if (level < 2) return null;
        float k = Math.min(1F, (level - 2) / 6F);
        return new Tier(0.004F + 0.016F * k, 0.075F + 0.165F * k, 1, 2 + (level >= 5 ? 1 : 0), 0.0F);
    }

    public static void register() {
        DungeonLevels.load();
        /* R6: natural-chest share moved from MODIFY loot pools to MODIFY_DROPS (loot.NaturalRefill) so it is a deterministic
           per-player roll (ShareSeed.natural, cycle 0) that the chest hints can preview. Same tiers, chances, weights and counts. */
        /* Roguelike Dungeons: per-dungeon, per-player budget appended when the chest's table is rolled (vanilla or Lootr). */
        LootTableEvents.MODIFY_DROPS.register((table, ctx, drops) -> {
            var k = table.unwrapKey();
            if (k.isEmpty() || !RoguelikeCompat.isRoguelikeTable(k.get().identifier()) || DungeonLevels.tierFor(k.get().identifier()) != null) return;
            try { DungeonLoot.onDrops(ctx, drops); } catch (Throwable e) { Infinium.LOG.warn("infinium: dungeon loot failed: " + e); }
        });
    }
    static void addFragmentPools(net.minecraft.world.level.storage.loot.LootTable.Builder builder, Tier t) {
        builder.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).when(LootItemRandomChanceCondition.randomChance(t.pF()))
            .add(LootItem.lootTableItem(Infinium.STRANGE_FRAGMENTS).apply(net.minecraft.world.level.storage.loot.functions.SetItemCountFunction
                .setCount(net.minecraft.world.level.storage.loot.providers.number.UniformGenerator.between(t.fMin(), t.fMax())))));
        if (t.pC() > 0) builder.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).when(LootItemRandomChanceCondition.randomChance(t.pC()))
            .add(LootItem.lootTableItem(Infinium.SPACETIME_CRYSTAL)));
    }
    private StyleLoot() {}
}
