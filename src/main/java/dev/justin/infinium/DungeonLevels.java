package dev.justin.infinium;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;

/**
 * Hook for custom generated dungeons: config/infinium_dungeon_loot.json
 *   {"tables": {"somemod:chests/dungeon_level_3": 3, ...},          explicit loot table id -> dungeon level
 *    "patterns": ["somemod:chests/dungeon_level_(\\d+)", ...]}       regex, group 1 = level
 * Level >= 2 gets StyleLoot.forLevel(level). "roguelike": per-dungeon budget keys (DungeonLoot). Missing keys keep defaults
 * (old R5b files with deepLevel/minFloor still load; those keys are ignored). Written with defaults on first start. Read once at mod init.
 */
public final class DungeonLevels {
    static final Map<String, Integer> TABLES = new LinkedHashMap<>();
    static final java.util.List<Pattern> PATTERNS = new java.util.ArrayList<>();
    static void load() {
        TABLES.clear(); PATTERNS.clear();   // R6: /infinium loot reload
        try {
            Path p = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("infinium_dungeon_loot.json");
            if (!Files.exists(p)) {
                Files.createDirectories(p.getParent());
                Files.writeString(p, "{\n  \"_doc\": \"tables: loot table id -> dungeon level; patterns: regex with group 1 = level. Level >= 2 gets Infinium loot scaling with level.\",\n  \"tables\": {},\n  \"patterns\": [],\n  \"reset\": {\"_doc\": \"Dungeon: rebuild after emptyMinutes empty; per-player loot refresh lootRefreshDays real days after first chest. Natural chests: per player+chest refill after a random naturalRefillMinRealDays..naturalRefillMaxRealDays real days.\", \"emptyMinutes\": 30, \"lootRefreshDays\": 7, \"naturalRefillMinRealDays\": 4, \"naturalRefillMaxRealDays\": 7},\n  \"roguelike\": {\"_doc\": \"Roguelike Dungeons per-dungeon budget, per player (15-25 fragments at random for a NORMAL dungeon; 40 dungeons to full Infinity; large/XL x sizeMultipliers). Depth bias: bottom floor weight = 1 + bias, top floor = 1.\", \"fragmentsMinNormalDungeon\": 15, \"fragmentsMaxNormalDungeon\": 25, \"fragmentsPerNormalDungeon\": 20.0, \"tier1TemplatesPerNormalDungeon\": 0.85, \"rareTemplatesPerNormalDungeon\": 0.42, \"sizeMultipliers\": [1.0, 1.5, 2.5], \"fragmentDepthBias\": 1.0, \"tier1DepthBias\": 0.5, \"rareDepthBias\": 4.0, \"fragmentSpread\": 0.667, \"largeRoomsPerFloor\": 15, \"xlRoomsPerFloor\": 18, \"fallbackChestsPerFloor\": 6}\n}\n");
                return;
            }
            JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            if (o.has("tables")) for (var e : o.getAsJsonObject("tables").entrySet()) TABLES.put(e.getKey(), e.getValue().getAsInt());
            if (o.has("roguelike")) { JsonObject r = o.getAsJsonObject("roguelike");
                DungeonLoot.FRAGMENTS_MIN_NORMAL = 15;
                DungeonLoot.FRAGMENTS_MAX_NORMAL = 25;
                DungeonLoot.FRAGMENTS_PER_NORMAL_DUNGEON = 20.0;
                if (r.has("fragmentsPerNormalDungeon")) DungeonLoot.FRAGMENTS_PER_NORMAL_DUNGEON = Math.max(0, r.get("fragmentsPerNormalDungeon").getAsDouble());
                if (r.has("fragmentsMinNormalDungeon")) DungeonLoot.FRAGMENTS_MIN_NORMAL = Math.max(0, r.get("fragmentsMinNormalDungeon").getAsInt());
                if (r.has("fragmentsMaxNormalDungeon")) DungeonLoot.FRAGMENTS_MAX_NORMAL = Math.max(DungeonLoot.FRAGMENTS_MIN_NORMAL, r.get("fragmentsMaxNormalDungeon").getAsInt());
                if (r.has("fragmentsMinNormalDungeon") || r.has("fragmentsMaxNormalDungeon"))
                    DungeonLoot.FRAGMENTS_PER_NORMAL_DUNGEON = (DungeonLoot.FRAGMENTS_MIN_NORMAL + DungeonLoot.FRAGMENTS_MAX_NORMAL) / 2.0;
                if (r.has("tier1TemplatesPerNormalDungeon")) DungeonLoot.TIER1_TEMPLATES_PER_NORMAL_DUNGEON = Math.max(0, r.get("tier1TemplatesPerNormalDungeon").getAsDouble());
                else DungeonLoot.TIER1_TEMPLATES_PER_NORMAL_DUNGEON = 0.85;
                if (r.has("rareTemplatesPerNormalDungeon")) DungeonLoot.RARE_TEMPLATES_PER_NORMAL_DUNGEON = Math.max(0, r.get("rareTemplatesPerNormalDungeon").getAsDouble());
                else DungeonLoot.RARE_TEMPLATES_PER_NORMAL_DUNGEON = 0.42;
                if (r.has("sizeMultipliers")) { var a = r.getAsJsonArray("sizeMultipliers"); for (int i = 0; i < 3 && i < a.size(); i++) DungeonLoot.SIZE_MULT[i] = Math.max(0, a.get(i).getAsDouble()); }
                if (r.has("fragmentDepthBias")) DungeonLoot.FRAGMENT_DEPTH_BIAS = Math.max(0, r.get("fragmentDepthBias").getAsDouble());
                if (r.has("tier1DepthBias")) DungeonLoot.TIER1_DEPTH_BIAS = Math.max(0, r.get("tier1DepthBias").getAsDouble());
                if (r.has("rareDepthBias")) DungeonLoot.RARE_DEPTH_BIAS = Math.max(0, r.get("rareDepthBias").getAsDouble());
                if (r.has("fragmentSpread")) DungeonLoot.FRAGMENT_SPREAD = Math.max(0, Math.min(1.9, r.get("fragmentSpread").getAsDouble()));
                if (r.has("largeRoomsPerFloor")) RoguelikeCompat.LARGE_ROOMS_PER_FLOOR = r.get("largeRoomsPerFloor").getAsDouble();
                if (r.has("xlRoomsPerFloor")) RoguelikeCompat.XL_ROOMS_PER_FLOOR = r.get("xlRoomsPerFloor").getAsDouble();
                if (r.has("fallbackChestsPerFloor")) DungeonLoot.FALLBACK_CHESTS_PER_FLOOR = Math.max(1, r.get("fallbackChestsPerFloor").getAsDouble()); }
            if (o.has("patterns")) for (var e : o.getAsJsonArray("patterns")) PATTERNS.add(Pattern.compile(e.getAsString()));
        } catch (Exception e) { System.err.println("[infinium] infinium_dungeon_loot.json: " + e); }
    }
    static StyleLoot.Tier tierFor(Identifier id) {
        String s = id.toString(); Integer lv = TABLES.get(s);
        if (lv == null) for (Pattern pt : PATTERNS) { Matcher m = pt.matcher(s); if (m.matches()) { try { lv = Integer.parseInt(m.group(1)); } catch (Exception ignored) {} break; } }
        return lv == null ? null : StyleLoot.forLevel(lv);
    }
    private DungeonLevels() {}
}
