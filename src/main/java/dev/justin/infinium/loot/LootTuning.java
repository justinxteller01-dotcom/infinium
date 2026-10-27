package dev.justin.infinium.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Reads config/infinium_dungeon_loot.json. Missing keys keep the defaults below.
 * Does not rewrite the file, so R5c "roguelike" keys stay put.
 */
public final class LootTuning {
    public static int emptyMinutes = 30;
    public static int lootRefreshDays = 7;     // R6c: dungeon per-player loot timer, real days
    public static int refillMinRealDays = 4;   // R6b: real (wall-clock) days
    public static int refillMaxRealDays = 7;
    public static int fragmentsMinNormal = 15;
    public static int fragmentsMaxNormal = 25;
    public static double fragmentsPerNormal = 20.0;
    public static double tier1PerNormal = 0.85;
    public static double rarePerNormal = 0.42;
    public static int largeRoomsPerFloor = 15;
    public static int xlRoomsPerFloor = 18;

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("infinium_dungeon_loot.json");
    }

    public static void load() {
        emptyMinutes = 30;
        lootRefreshDays = 7;
        refillMinRealDays = 4;
        refillMaxRealDays = 7;
        fragmentsMinNormal = 15;
        fragmentsMaxNormal = 25;
        fragmentsPerNormal = 20.0;
        tier1PerNormal = 0.85;
        rarePerNormal = 0.42;
        largeRoomsPerFloor = 15;
        xlRoomsPerFloor = 18;
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject reset = root.get("reset") instanceof JsonObject o ? o : root;
            emptyMinutes = num(reset, "emptyMinutes", num(reset, "dungeonEmptyMinutes", emptyMinutes));
            lootRefreshDays = Math.max(1, num(reset, "lootRefreshDays", lootRefreshDays));   // old lootRefreshHours ignored
            // old in-game-day keys (naturalRefillMinDays/MaxDays) are ignored
            refillMinRealDays = Math.max(1, num(reset, "naturalRefillMinRealDays", refillMinRealDays));
            refillMaxRealDays = num(reset, "naturalRefillMaxRealDays", refillMaxRealDays);
            if (root.get("roguelike") instanceof JsonObject rg) {
                fragmentsMinNormal = Math.max(0, num(rg, "fragmentsMinNormalDungeon", fragmentsMinNormal));
                fragmentsMaxNormal = Math.max(fragmentsMinNormal, num(rg, "fragmentsMaxNormalDungeon", fragmentsMaxNormal));
                fragmentsPerNormal = dbl(rg, "fragmentsPerNormalDungeon", (fragmentsMinNormal + fragmentsMaxNormal) / 2.0);
                tier1PerNormal = dbl(rg, "tier1TemplatesPerNormalDungeon", tier1PerNormal);
                rarePerNormal = dbl(rg, "rareTemplatesPerNormalDungeon", rarePerNormal);
                largeRoomsPerFloor = num(rg, "largeRoomsPerFloor", largeRoomsPerFloor);
                xlRoomsPerFloor = num(rg, "xlRoomsPerFloor", xlRoomsPerFloor);
            }
            if (refillMaxRealDays < refillMinRealDays) refillMaxRealDays = refillMinRealDays;
        } catch (Exception e) {
            dev.justin.infinium.Infinium.LOG.warn("infinium_dungeon_loot.json unreadable, defaults kept: {}", e.toString());
        }
    }

    public static int sizeClass(double roomsPerFloor) {
        if (roomsPerFloor >= xlRoomsPerFloor) return 2;
        if (roomsPerFloor >= largeRoomsPerFloor) return 1;
        return 0;
    }

    private static int num(JsonObject o, String key, int fallback) {
        if (!(o.get(key) instanceof JsonElement e) || !e.isJsonPrimitive()) return fallback;
        try { return e.getAsInt(); } catch (Exception ex) { return fallback; }
    }

    private static double dbl(JsonObject o, String key, double fallback) {
        if (!(o.get(key) instanceof JsonElement e) || !e.isJsonPrimitive()) return fallback;
        try { return e.getAsDouble(); } catch (Exception ex) { return fallback; }
    }

    private LootTuning() {}
}
