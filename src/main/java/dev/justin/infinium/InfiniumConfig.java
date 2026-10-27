package dev.justin.infinium;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Server / gameplay configuration for Infinium: config/infinium.json.
 * Manages all gameplay-affecting numeric values (meteor shower, crystals, perks, durability, flight).
 * Synced from server to clients on join via InfiniumConfigSyncPayload.
 */
public final class InfiniumConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // --- Meteor Shower ---
    private static float meteorShowerChance = 1.0f;
    private static int meteorMinDays = 10;
    private static int meteorMaxDays = 15;
    private static float meteorDirectDamage = 2.0f;
    private static float meteorLootChance = 0.075f;
    private static int meteorMinFragments = 1;
    private static int meteorMaxFragments = 3;
    private static float meteorTier1TemplateChance = 0.008f;
    private static float meteorTier2TemplateChance = 0.002f;
    private static int meteorBubbleRadius = 160;

    // --- Chest Loot & Templates ---
    private static double worldChestFragmentRate = 1.0;
    private static double dungeonChestFragmentRate = 1.0;
    private static double templateRateAstral = 1.0;
    private static double templateRateCybernetic = 1.0;
    private static double templateRateSingularity = 1.0;
    private static double templateRateCyberpsycho = 1.0;

    // --- Experimental / Advanced ---
    private static int dungeonEmptyMinutes = 30;
    private static int chestRefillMinDays = 4;
    private static int chestRefillMaxDays = 7;

    // --- Crystal Capacities ---

    // --- Gameplay & Flight ---
    private static int durabilityFloor = 50;
    private static double flightRampSeconds = 2.5;

    // --- Combat Perks ---
    private static double miningBonusT1 = 0.10;
    private static double miningBonusT2 = 0.20;
    private static double miningBonusT3 = 0.30;

    /** G4b: set Jump perk = air jumps (Cybernetic / Cyber Psycho / Infinity). Replaces the old jump_t1..t3 height bonus. */
    private static int airJumpsT1 = 1;
    private static int airJumpsT2 = 2;
    private static int airJumpsT3 = 3;

    private static double attackBonusT1 = 0.20;
    private static double attackBonusT2 = 0.50;
    private static double attackBonusT3 = 0.75;

    private static double slowAuraT1 = 0.25;
    private static double slowAuraT2 = 0.50;
    private static double slowAuraT3 = 0.75;
    private static int slowDurationTicks = 60;
    private static int slowCooldownTicks = 120;
    private static int slowRadius = 16;

    private static double legSpeedAstral = 0.05;
    private static double legSpeedCybernetic = 0.10;
    private static double legSpeedCyberpsycho = 0.15;
    private static double legSpeedInfinity = 0.20;

    private static double damageTakenT1 = 0.667;
    private static double damageTakenT2 = 0.571;
    private static double damageTakenT3 = 0.500;

    // --- Utility Perks ---
    private static int repairIntervalT1 = 10;
    private static int repairIntervalT2 = 5;
    private static int repairIntervalT3 = 2;

    private static double keepChanceT1 = 0.11;
    private static double keepChanceT2 = 0.22;
    private static double keepChanceT3 = 0.33;

    private static double reachBonusT1 = 1.0;
    private static double reachBonusT2 = 2.0;
    private static double reachBonusT3 = 3.0;

    static {
        resetDefaults();
    }

    public static void resetDefaults() {
        meteorShowerChance = 1.0f;
        meteorMinDays = 10;
        meteorMaxDays = 15;
        meteorDirectDamage = 2.0f;
        meteorLootChance = 0.075f;
        meteorMinFragments = 1;
        meteorMaxFragments = 3;
        meteorTier1TemplateChance = 0.008f;
        meteorTier2TemplateChance = 0.002f;
        meteorBubbleRadius = 160;

        worldChestFragmentRate = 1.0;
        dungeonChestFragmentRate = 1.0;
        templateRateAstral = 1.0;
        templateRateCybernetic = 1.0;
        templateRateSingularity = 1.0;
        templateRateCyberpsycho = 1.0;

        dungeonEmptyMinutes = 30;
        chestRefillMinDays = 4;
        chestRefillMaxDays = 7;


        durabilityFloor = 50;
        flightRampSeconds = 2.5;

        miningBonusT1 = 0.10;
        miningBonusT2 = 0.20;
        miningBonusT3 = 0.30;

        airJumpsT1 = 1;
        airJumpsT2 = 2;
        airJumpsT3 = 3;

        attackBonusT1 = 0.20;
        attackBonusT2 = 0.50;
        attackBonusT3 = 0.75;

        slowAuraT1 = 0.25;
        slowAuraT2 = 0.50;
        slowAuraT3 = 0.75;
        slowDurationTicks = 60;
        slowCooldownTicks = 120;
        slowRadius = 16;

        legSpeedAstral = 0.05;
        legSpeedCybernetic = 0.10;
        legSpeedCyberpsycho = 0.15;
        legSpeedInfinity = 0.20;

        damageTakenT1 = 0.667;
        damageTakenT2 = 0.571;
        damageTakenT3 = 0.500;

        repairIntervalT1 = 10;
        repairIntervalT2 = 5;
        repairIntervalT3 = 2;

        keepChanceT1 = 0.11;
        keepChanceT2 = 0.22;
        keepChanceT3 = 0.33;

        reachBonusT1 = 1.0;
        reachBonusT2 = 2.0;
        reachBonusT3 = 3.0;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("infinium.json");
    }

    public static void load() {
        resetDefaults();
        try {
            Path f = file();
            if (!Files.exists(f)) {
                save();
                return;
            }
            String content = Files.readString(f, StandardCharsets.UTF_8);
            loadFromJson(content);
        } catch (Exception e) {
            Infinium.LOG.warn("infinium.json unreadable, using defaults: {}", e.toString());
        }
    }

    public static void loadFromJson(String json) {
        if (json == null || json.isBlank()) return;
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.get("meteor_shower") instanceof JsonObject mo) {
                meteorShowerChance = floatVal(mo, "chance", meteorShowerChance);
                meteorMinDays = intVal(mo, "min_days", meteorMinDays);
                meteorMaxDays = intVal(mo, "max_days", meteorMaxDays);
                meteorDirectDamage = floatVal(mo, "direct_damage", meteorDirectDamage);
                meteorLootChance = floatVal(mo, "loot_chance", meteorLootChance);
                meteorMinFragments = intVal(mo, "min_fragments", meteorMinFragments);
                meteorMaxFragments = intVal(mo, "max_fragments", meteorMaxFragments);
                meteorTier1TemplateChance = floatVal(mo, "t1_template_chance", meteorTier1TemplateChance);
                meteorTier2TemplateChance = floatVal(mo, "t2_template_chance", meteorTier2TemplateChance);
                meteorBubbleRadius = intVal(mo, "bubble_radius", meteorBubbleRadius);
            }
            if (o.get("loot_and_templates") instanceof JsonObject lo) {
                worldChestFragmentRate = doubleVal(lo, "world_fragments", worldChestFragmentRate);
                dungeonChestFragmentRate = doubleVal(lo, "dungeon_fragments", dungeonChestFragmentRate);
                templateRateAstral = doubleVal(lo, "astral_template", templateRateAstral);
                templateRateCybernetic = doubleVal(lo, "cybernetic_template", templateRateCybernetic);
                templateRateSingularity = doubleVal(lo, "singularity_template", templateRateSingularity);
                templateRateCyberpsycho = doubleVal(lo, "cyberpsycho_template", templateRateCyberpsycho);
            }
            if (o.get("experimental") instanceof JsonObject eo) {
                dungeonEmptyMinutes = intVal(eo, "dungeon_empty_minutes", dungeonEmptyMinutes);
                chestRefillMinDays = intVal(eo, "chest_refill_min_days", chestRefillMinDays);
                chestRefillMaxDays = intVal(eo, "chest_refill_max_days", chestRefillMaxDays);
                dev.justin.infinium.loot.LootTuning.emptyMinutes = dungeonEmptyMinutes;
                dev.justin.infinium.loot.LootTuning.refillMinRealDays = chestRefillMinDays;
                dev.justin.infinium.loot.LootTuning.refillMaxRealDays = chestRefillMaxDays;
            }
            if (o.get("crystals") instanceof JsonObject co) {
            }
            if (o.get("gameplay") instanceof JsonObject go) {
                durabilityFloor = intVal(go, "durability_floor", durabilityFloor);
                flightRampSeconds = doubleVal(go, "flight_ramp_seconds", flightRampSeconds);
            }
            if (o.get("combat_perks") instanceof JsonObject cpo) {
                miningBonusT1 = doubleVal(cpo, "mining_t1", miningBonusT1);
                miningBonusT2 = doubleVal(cpo, "mining_t2", miningBonusT2);
                miningBonusT3 = doubleVal(cpo, "mining_t3", miningBonusT3);
                airJumpsT1 = intVal(cpo, "air_jumps_t1", airJumpsT1);
                airJumpsT2 = intVal(cpo, "air_jumps_t2", airJumpsT2);
                airJumpsT3 = intVal(cpo, "air_jumps_t3", airJumpsT3);
                attackBonusT1 = doubleVal(cpo, "attack_t1", attackBonusT1);
                attackBonusT2 = doubleVal(cpo, "attack_t2", attackBonusT2);
                attackBonusT3 = doubleVal(cpo, "attack_t3", attackBonusT3);
                slowAuraT1 = doubleVal(cpo, "slow_aura_t1", slowAuraT1);
                slowAuraT2 = doubleVal(cpo, "slow_aura_t2", slowAuraT2);
                slowAuraT3 = doubleVal(cpo, "slow_aura_t3", slowAuraT3);
                slowDurationTicks = intVal(cpo, "slow_duration_ticks", slowDurationTicks);
                slowCooldownTicks = intVal(cpo, "slow_cooldown_ticks", slowCooldownTicks);
                slowRadius = intVal(cpo, "slow_radius", slowRadius);
                legSpeedAstral = doubleVal(cpo, "leg_speed_astral", legSpeedAstral);
                legSpeedCybernetic = doubleVal(cpo, "leg_speed_cybernetic", legSpeedCybernetic);
                legSpeedCyberpsycho = doubleVal(cpo, "leg_speed_cyberpsycho", legSpeedCyberpsycho);
                legSpeedInfinity = doubleVal(cpo, "leg_speed_infinity", legSpeedInfinity);
                damageTakenT1 = doubleVal(cpo, "damage_taken_t1", damageTakenT1);
                damageTakenT2 = doubleVal(cpo, "damage_taken_t2", damageTakenT2);
                damageTakenT3 = doubleVal(cpo, "damage_taken_t3", damageTakenT3);
            }
            if (o.get("utility_perks") instanceof JsonObject upo) {
                repairIntervalT1 = intVal(upo, "repair_interval_t1", repairIntervalT1);
                repairIntervalT2 = intVal(upo, "repair_interval_t2", repairIntervalT2);
                repairIntervalT3 = intVal(upo, "repair_interval_t3", repairIntervalT3);
                keepChanceT1 = doubleVal(upo, "keep_chance_t1", keepChanceT1);
                keepChanceT2 = doubleVal(upo, "keep_chance_t2", keepChanceT2);
                keepChanceT3 = doubleVal(upo, "keep_chance_t3", keepChanceT3);
                reachBonusT1 = doubleVal(upo, "reach_bonus_t1", reachBonusT1);
                reachBonusT2 = doubleVal(upo, "reach_bonus_t2", reachBonusT2);
                reachBonusT3 = doubleVal(upo, "reach_bonus_t3", reachBonusT3);
            }
        } catch (Exception e) {
            Infinium.LOG.warn("Failed to parse Infinium config JSON: {}", e.toString());
        }
    }

    public static String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("version", 1);

        JsonObject mo = new JsonObject();
        mo.addProperty("chance", meteorShowerChance);
        mo.addProperty("min_days", meteorMinDays);
        mo.addProperty("max_days", meteorMaxDays);
        mo.addProperty("direct_damage", meteorDirectDamage);
        mo.addProperty("loot_chance", meteorLootChance);
        mo.addProperty("min_fragments", meteorMinFragments);
        mo.addProperty("max_fragments", meteorMaxFragments);
        mo.addProperty("t1_template_chance", meteorTier1TemplateChance);
        mo.addProperty("t2_template_chance", meteorTier2TemplateChance);
        mo.addProperty("bubble_radius", meteorBubbleRadius);
        o.add("meteor_shower", mo);

        JsonObject lo = new JsonObject();
        lo.addProperty("world_fragments", worldChestFragmentRate);
        lo.addProperty("dungeon_fragments", dungeonChestFragmentRate);
        lo.addProperty("astral_template", templateRateAstral);
        lo.addProperty("cybernetic_template", templateRateCybernetic);
        lo.addProperty("singularity_template", templateRateSingularity);
        lo.addProperty("cyberpsycho_template", templateRateCyberpsycho);
        o.add("loot_and_templates", lo);

        JsonObject co = new JsonObject();
        o.add("crystals", co);

        JsonObject go = new JsonObject();
        go.addProperty("durability_floor", durabilityFloor);
        go.addProperty("flight_ramp_seconds", flightRampSeconds);
        o.add("gameplay", go);

        JsonObject cpo = new JsonObject();
        cpo.addProperty("mining_t1", miningBonusT1);
        cpo.addProperty("mining_t2", miningBonusT2);
        cpo.addProperty("mining_t3", miningBonusT3);
        cpo.addProperty("air_jumps_t1", airJumpsT1);
        cpo.addProperty("air_jumps_t2", airJumpsT2);
        cpo.addProperty("air_jumps_t3", airJumpsT3);
        cpo.addProperty("attack_t1", attackBonusT1);
        cpo.addProperty("attack_t2", attackBonusT2);
        cpo.addProperty("attack_t3", attackBonusT3);
        cpo.addProperty("slow_aura_t1", slowAuraT1);
        cpo.addProperty("slow_aura_t2", slowAuraT2);
        cpo.addProperty("slow_aura_t3", slowAuraT3);
        cpo.addProperty("slow_duration_ticks", slowDurationTicks);
        cpo.addProperty("slow_cooldown_ticks", slowCooldownTicks);
        cpo.addProperty("slow_radius", slowRadius);
        cpo.addProperty("leg_speed_astral", legSpeedAstral);
        cpo.addProperty("leg_speed_cybernetic", legSpeedCybernetic);
        cpo.addProperty("leg_speed_cyberpsycho", legSpeedCyberpsycho);
        cpo.addProperty("leg_speed_infinity", legSpeedInfinity);
        cpo.addProperty("damage_taken_t1", damageTakenT1);
        cpo.addProperty("damage_taken_t2", damageTakenT2);
        cpo.addProperty("damage_taken_t3", damageTakenT3);
        o.add("combat_perks", cpo);

        JsonObject upo = new JsonObject();
        upo.addProperty("repair_interval_t1", repairIntervalT1);
        upo.addProperty("repair_interval_t2", repairIntervalT2);
        upo.addProperty("repair_interval_t3", repairIntervalT3);
        upo.addProperty("keep_chance_t1", keepChanceT1);
        upo.addProperty("keep_chance_t2", keepChanceT2);
        upo.addProperty("keep_chance_t3", keepChanceT3);
        upo.addProperty("reach_bonus_t1", reachBonusT1);
        upo.addProperty("reach_bonus_t2", reachBonusT2);
        upo.addProperty("reach_bonus_t3", reachBonusT3);
        o.add("utility_perks", upo);

        JsonObject eo = new JsonObject();
        eo.addProperty("dungeon_empty_minutes", dungeonEmptyMinutes);
        eo.addProperty("chest_refill_min_days", chestRefillMinDays);
        eo.addProperty("chest_refill_max_days", chestRefillMaxDays);
        o.add("experimental", eo);

        return GSON.toJson(o);
    }

    public static void save() {
        try {
            Path f = file();
            Path dir = f.getParent();
            if (dir != null && !Files.exists(dir)) Files.createDirectories(dir);
            Path tmp = f.resolveSibling("infinium.json.tmp");
            Files.writeString(tmp, toJson(), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Infinium.LOG.warn("Failed to write infinium.json: {}", e.toString());
        }
    }

    private static int intVal(JsonObject o, String key, int fallback) {
        if (o.get(key) instanceof JsonElement e && e.isJsonPrimitive()) return e.getAsInt();
        return fallback;
    }

    private static float floatVal(JsonObject o, String key, float fallback) {
        if (o.get(key) instanceof JsonElement e && e.isJsonPrimitive()) return e.getAsFloat();
        return fallback;
    }

    private static double doubleVal(JsonObject o, String key, double fallback) {
        if (o.get(key) instanceof JsonElement e && e.isJsonPrimitive()) return e.getAsDouble();
        return fallback;
    }

    // --- Getters & Setters ---
    public static int getMeteorMinDays() { return Math.max(1, meteorMinDays); }
    public static void setMeteorMinDays(int v) { meteorMinDays = Math.max(1, v); if (meteorMaxDays < meteorMinDays) meteorMaxDays = meteorMinDays; }

    public static int getMeteorMaxDays() { return Math.max(meteorMinDays, meteorMaxDays); }
    public static void setMeteorMaxDays(int v) { meteorMaxDays = Math.max(meteorMinDays, v); }

    public static float getMeteorDirectDamage() { return Math.max(0.0f, meteorDirectDamage); }
    public static void setMeteorDirectDamage(float v) { meteorDirectDamage = Math.max(0.0f, v); }

    public static float getMeteorLootChance() { return Math.max(0.0f, Math.min(1.0f, meteorLootChance)); }
    public static void setMeteorLootChance(float v) { meteorLootChance = Math.max(0.0f, Math.min(1.0f, v)); }

    public static int getMeteorMinFragments() { return Math.max(0, meteorMinFragments); }
    public static void setMeteorMinFragments(int v) { meteorMinFragments = Math.max(0, v); if (meteorMaxFragments < meteorMinFragments) meteorMaxFragments = meteorMinFragments; }

    public static int getMeteorMaxFragments() { return Math.max(meteorMinFragments, meteorMaxFragments); }
    public static void setMeteorMaxFragments(int v) { meteorMaxFragments = Math.max(meteorMinFragments, v); }

    public static float getMeteorTier1TemplateChance() { return Math.max(0.0f, Math.min(1.0f, meteorTier1TemplateChance)); }
    public static void setMeteorTier1TemplateChance(float v) { meteorTier1TemplateChance = Math.max(0.0f, Math.min(1.0f, v)); }

    public static float getMeteorTier2TemplateChance() { return Math.max(0.0f, Math.min(1.0f, meteorTier2TemplateChance)); }
    public static void setMeteorTier2TemplateChance(float v) { meteorTier2TemplateChance = Math.max(0.0f, Math.min(1.0f, v)); }

    public static int getMeteorBubbleRadius() { return Math.max(32, meteorBubbleRadius); }
    public static void setMeteorBubbleRadius(int v) { meteorBubbleRadius = Math.max(32, v); }




    public static int getDurabilityFloor() { return Math.max(1, durabilityFloor); }
    public static void setDurabilityFloor(int v) { durabilityFloor = Math.max(1, v); }


    public static double getFlightRampSeconds() { return Math.max(0.5, flightRampSeconds); }
    public static void setFlightRampSeconds(double v) { flightRampSeconds = Math.max(0.5, v); }

    public static double getMiningBonusT1() { return Math.max(0.0, miningBonusT1); }
    public static void setMiningBonusT1(double v) { miningBonusT1 = Math.max(0.0, v); }

    public static double getMiningBonusT2() { return Math.max(0.0, miningBonusT2); }
    public static void setMiningBonusT2(double v) { miningBonusT2 = Math.max(0.0, v); }

    public static double getMiningBonusT3() { return Math.max(0.0, miningBonusT3); }
    public static void setMiningBonusT3(double v) { miningBonusT3 = Math.max(0.0, v); }

    public static int getAirJumpsT1() { return Math.max(0, airJumpsT1); }
    public static void setAirJumpsT1(int v) { airJumpsT1 = Math.max(0, v); }

    public static int getAirJumpsT2() { return Math.max(0, airJumpsT2); }
    public static void setAirJumpsT2(int v) { airJumpsT2 = Math.max(0, v); }

    public static int getAirJumpsT3() { return Math.max(0, airJumpsT3); }
    public static void setAirJumpsT3(int v) { airJumpsT3 = Math.max(0, v); }

    public static double getAttackBonusT1() { return Math.max(0.0, attackBonusT1); }
    public static void setAttackBonusT1(double v) { attackBonusT1 = Math.max(0.0, v); }

    public static double getAttackBonusT2() { return Math.max(0.0, attackBonusT2); }
    public static void setAttackBonusT2(double v) { attackBonusT2 = Math.max(0.0, v); }

    public static double getAttackBonusT3() { return Math.max(0.0, attackBonusT3); }
    public static void setAttackBonusT3(double v) { attackBonusT3 = Math.max(0.0, v); }

    public static double getSlowAuraT1() { return Math.max(0.0, Math.min(1.0, slowAuraT1)); }
    public static void setSlowAuraT1(double v) { slowAuraT1 = Math.max(0.0, Math.min(1.0, v)); }

    public static double getSlowAuraT2() { return Math.max(0.0, Math.min(1.0, slowAuraT2)); }
    public static void setSlowAuraT2(double v) { slowAuraT2 = Math.max(0.0, Math.min(1.0, v)); }

    public static double getSlowAuraT3() { return Math.max(0.0, Math.min(1.0, slowAuraT3)); }
    public static void setSlowAuraT3(double v) { slowAuraT3 = Math.max(0.0, Math.min(1.0, v)); }

    public static int getSlowDurationTicks() { return Math.max(10, slowDurationTicks); }
    public static void setSlowDurationTicks(int v) { slowDurationTicks = Math.max(10, v); }

    public static int getSlowCooldownTicks() { return Math.max(10, slowCooldownTicks); }
    public static void setSlowCooldownTicks(int v) { slowCooldownTicks = Math.max(10, v); }

    public static int getSlowRadius() { return Math.max(2, slowRadius); }
    public static void setSlowRadius(int v) { slowRadius = Math.max(2, v); }

    public static double getLegSpeedAstral() { return Math.max(0.0, legSpeedAstral); }
    public static void setLegSpeedAstral(double v) { legSpeedAstral = Math.max(0.0, v); }

    public static double getLegSpeedCybernetic() { return Math.max(0.0, legSpeedCybernetic); }
    public static void setLegSpeedCybernetic(double v) { legSpeedCybernetic = Math.max(0.0, v); }

    public static double getLegSpeedCyberpsycho() { return Math.max(0.0, legSpeedCyberpsycho); }
    public static void setLegSpeedCyberpsycho(double v) { legSpeedCyberpsycho = Math.max(0.0, v); }

    public static double getLegSpeedInfinity() { return Math.max(0.0, legSpeedInfinity); }
    public static void setLegSpeedInfinity(double v) { legSpeedInfinity = Math.max(0.0, v); }

    public static double getDamageTakenT1() { return Math.max(0.05, Math.min(1.0, damageTakenT1)); }
    public static void setDamageTakenT1(double v) { damageTakenT1 = Math.max(0.05, Math.min(1.0, v)); }

    public static double getDamageTakenT2() { return Math.max(0.05, Math.min(1.0, damageTakenT2)); }
    public static void setDamageTakenT2(double v) { damageTakenT2 = Math.max(0.05, Math.min(1.0, v)); }

    public static double getDamageTakenT3() { return Math.max(0.05, Math.min(1.0, damageTakenT3)); }
    public static void setDamageTakenT3(double v) { damageTakenT3 = Math.max(0.05, Math.min(1.0, v)); }

    public static int getRepairIntervalT1() { return Math.max(1, repairIntervalT1); }
    public static void setRepairIntervalT1(int v) { repairIntervalT1 = Math.max(1, v); }

    public static int getRepairIntervalT2() { return Math.max(1, repairIntervalT2); }
    public static void setRepairIntervalT2(int v) { repairIntervalT2 = Math.max(1, v); }

    public static int getRepairIntervalT3() { return Math.max(1, repairIntervalT3); }
    public static void setRepairIntervalT3(int v) { repairIntervalT3 = Math.max(1, v); }

    public static double getKeepChanceT1() { return Math.max(0.0, Math.min(1.0, keepChanceT1)); }
    public static void setKeepChanceT1(double v) { keepChanceT1 = Math.max(0.0, Math.min(1.0, v)); }

    public static double getKeepChanceT2() { return Math.max(0.0, Math.min(1.0, keepChanceT2)); }
    public static void setKeepChanceT2(double v) { keepChanceT2 = Math.max(0.0, Math.min(1.0, v)); }

    public static double getKeepChanceT3() { return Math.max(0.0, Math.min(1.0, keepChanceT3)); }
    public static void setKeepChanceT3(double v) { keepChanceT3 = Math.max(0.0, Math.min(1.0, v)); }

    public static double getReachBonusT1() { return Math.max(0.0, reachBonusT1); }
    public static void setReachBonusT1(double v) { reachBonusT1 = Math.max(0.0, v); }

    public static double getReachBonusT2() { return Math.max(0.0, reachBonusT2); }
    public static void setReachBonusT2(double v) { reachBonusT2 = Math.max(0.0, v); }

    public static double getReachBonusT3() { return Math.max(0.0, reachBonusT3); }
    public static void setReachBonusT3(double v) { reachBonusT3 = Math.max(0.0, v); }

    public static float getMeteorShowerChance() { return Math.max(0.0f, Math.min(2.0f, meteorShowerChance)); }
    public static void setMeteorShowerChance(float v) { meteorShowerChance = Math.max(0.0f, Math.min(2.0f, v)); }

    public static double getWorldChestFragmentRate() { return Math.max(0.0, Math.min(3.0, worldChestFragmentRate)); }
    public static void setWorldChestFragmentRate(double v) { worldChestFragmentRate = Math.max(0.0, Math.min(3.0, v)); }

    public static double getDungeonChestFragmentRate() { return Math.max(0.0, Math.min(3.0, dungeonChestFragmentRate)); }
    public static void setDungeonChestFragmentRate(double v) { dungeonChestFragmentRate = Math.max(0.0, Math.min(3.0, v)); }

    public static double getTemplateRateAstral() { return Math.max(0.0, Math.min(3.0, templateRateAstral)); }
    public static void setTemplateRateAstral(double v) { templateRateAstral = Math.max(0.0, Math.min(3.0, v)); }

    public static double getTemplateRateCybernetic() { return Math.max(0.0, Math.min(3.0, templateRateCybernetic)); }
    public static void setTemplateRateCybernetic(double v) { templateRateCybernetic = Math.max(0.0, Math.min(3.0, v)); }

    public static double getTemplateRateSingularity() { return Math.max(0.0, Math.min(3.0, templateRateSingularity)); }
    public static void setTemplateRateSingularity(double v) { templateRateSingularity = Math.max(0.0, Math.min(3.0, v)); }

    public static double getTemplateRateCyberpsycho() { return Math.max(0.0, Math.min(3.0, templateRateCyberpsycho)); }
    public static void setTemplateRateCyberpsycho(double v) { templateRateCyberpsycho = Math.max(0.0, Math.min(3.0, v)); }

    public static int getDungeonEmptyMinutes() { return Math.max(5, Math.min(120, dungeonEmptyMinutes)); }
    public static void setDungeonEmptyMinutes(int v) {
        dungeonEmptyMinutes = Math.max(5, Math.min(120, v));
        dev.justin.infinium.loot.LootTuning.emptyMinutes = dungeonEmptyMinutes;
    }

    public static int getChestRefillMinDays() { return Math.max(1, Math.min(14, chestRefillMinDays)); }
    public static void setChestRefillMinDays(int v) {
        chestRefillMinDays = Math.max(1, Math.min(14, v));
        dev.justin.infinium.loot.LootTuning.refillMinRealDays = chestRefillMinDays;
    }

    public static int getChestRefillMaxDays() { return Math.max(1, Math.min(14, chestRefillMaxDays)); }
    public static void setChestRefillMaxDays(int v) {
        chestRefillMaxDays = Math.max(1, Math.min(14, v));
        dev.justin.infinium.loot.LootTuning.refillMaxRealDays = chestRefillMaxDays;
    }

    private InfiniumConfig() {}
}
