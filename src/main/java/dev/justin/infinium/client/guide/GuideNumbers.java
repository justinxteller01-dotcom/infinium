package dev.justin.infinium.client.guide;

import dev.justin.infinium.CrystalCharge;
import dev.justin.infinium.Info;
import dev.justin.infinium.InfiniumConfig;
import dev.justin.infinium.PerkValues;
import dev.justin.infinium.PerkValues.Family;
import dev.justin.infinium.SpacetimeWarp;
import dev.justin.infinium.ability.XrayPulse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Every number the guidebook prints. pages.json writes {n:key}; this table resolves it.
 * SINGLE PLACE TO CHANGE GUIDE NUMBERS. Most values are read live from PerkValues / InfiniumConfig so the
 * book follows the game; a few have no code constant yet and are literals here (marked LITERAL).
 * Unknown keys render as "[?key]" so a typo is visible in game.
 * Also {key:id} -> bound key name (Info.key), e.g. {key:xray_pulse}.
 */
public final class GuideNumbers {
    private static final Map<String, Supplier<String>> N = new LinkedHashMap<>();
    private static final Family[] T_A = {Family.ASTRAL, Family.SINGULARITY, Family.INFINITY};
    private static final Family[] T_C = {Family.CYBERNETIC, Family.CYBERPSYCHO, Family.INFINITY};

    static {
        for (int i = 0; i < 3; i++) {
            final int t = i; final Family a = T_A[i], c = T_C[i];
            String s = "_t" + (i + 1);
            put("dmg" + s, () -> pct1(PerkValues.damageTaken(c)));
            put("xray" + s, () -> String.valueOf(XrayPulse.RADII[t]));
            put("tunnel" + s, () -> String.valueOf((int) SpacetimeWarp.DISTANCE[t]));
            put("tunnel_cd" + s, () -> num(SpacetimeWarp.COOLDOWN_SEC[t]));
            put("repair" + s, () -> String.valueOf(PerkValues.repairTicks(a) / 20));
            put("keep" + s, () -> pct(PerkValues.keepChance(a)));
            put("reach" + s, () -> num(PerkValues.reach(a)));
            put("stack64" + s, () -> String.valueOf(PerkValues.boostedMax(a, 64)));
            put("stack16" + s, () -> String.valueOf(PerkValues.boostedMax(a, 16)));
            put("stack1" + s, () -> String.valueOf(PerkValues.boostedMax(a, 1)));
            put("mining" + s, () -> pct(PerkValues.mining(c)));
            put("airjumps" + s, () -> String.valueOf(PerkValues.airJumps(c)));
            put("attack" + s, () -> pct(PerkValues.attackAndBow(c)));
            put("slow" + s, () -> pct(PerkValues.slowAmount(c)));
            put("slowdur" + s, () -> String.valueOf(PerkValues.slowDurationTicks(c) / 20));
            put("slowcd" + s, () -> String.valueOf(PerkValues.slowCooldownTicks(c) / 20));
            put("slash" + s, () -> new String[]{"60%", "70%", "80%"}[t]);   // LITERAL (EnergySlashEntity comment)
        }
        put("speed_astral", () -> pct(PerkValues.legSpeed(Family.ASTRAL)));
        put("speed_cyber", () -> pct(PerkValues.legSpeed(Family.CYBERNETIC)));
        put("speed_sing", () -> pct(PerkValues.legSpeed(Family.SINGULARITY)));
        put("speed_cp", () -> pct(PerkValues.legSpeed(Family.CYBERPSYCHO)));
        put("speed_inf", () -> pct(PerkValues.legSpeed(Family.INFINITY)));
        put("jump_astral", () -> "2");    // LITERAL hold-jump height (Info boots lines)
        put("jump_sing", () -> "3");      // LITERAL
        put("jump_inf", () -> "4");       // LITERAL
        put("slow_radius", () -> String.valueOf(PerkValues.slowRadius()));
        put("durability_floor", () -> String.valueOf(PerkValues.durabilityFloor()));
        put("crystals_t1", () -> String.valueOf(CrystalCharge.T1_SLOTS));
        put("crystals_t2", () -> String.valueOf(CrystalCharge.T2_SLOTS));
        put("crystals_t3", () -> String.valueOf(CrystalCharge.T3_SLOTS));
        put("crystals_total", () -> String.valueOf(2 * CrystalCharge.T1_SLOTS + 2 * CrystalCharge.T2_SLOTS + CrystalCharge.T3_SLOTS));
        put("meteor_min_days", () -> String.valueOf(InfiniumConfig.getMeteorMinDays()));
        put("meteor_max_days", () -> String.valueOf(InfiniumConfig.getMeteorMaxDays()));
        put("meteor_hit_hearts", () -> num(InfiniumConfig.getMeteorDirectDamage() / 2.0));
        put("meteor_loot", () -> pct1(InfiniumConfig.getMeteorLootChance()));
        put("meteor_frag_min", () -> String.valueOf(InfiniumConfig.getMeteorMinFragments()));
        put("meteor_frag_max", () -> String.valueOf(InfiniumConfig.getMeteorMaxFragments()));
        put("meteor_tpl_t1", () -> pct1(InfiniumConfig.getMeteorTier1TemplateChance()));
        put("meteor_tpl_t2", () -> pct1(InfiniumConfig.getMeteorTier2TemplateChance()));
        put("meteor_warning_min", () -> "2");   // LITERAL
        put("dungeon_refill_days", () -> String.valueOf(dev.justin.infinium.loot.LootTuning.lootRefreshDays));
        put("sword_dmg", () -> num(dev.justin.infinium.Infinium.SWORD_BASE_DAMAGE));
        put("sword_dmg_t2", () -> num(dev.justin.infinium.Infinium.SWORD_BASE_DAMAGE + dev.justin.infinium.Infinium.swordSetBonus(dev.justin.infinium.SetLook.SINGULARITY)));
        put("sword_dmg_t3", () -> num(dev.justin.infinium.Infinium.SWORD_BASE_DAMAGE + dev.justin.infinium.Infinium.swordSetBonus(dev.justin.infinium.SetLook.INFINITY)));
        put("bleed_s", () -> "3-5");        // LITERAL (Bleed/Cyberburn, BleedingEffect.durationTicks: 3s Astral/Cybernetic, 4s Singularity/Cyber Psycho, 5s Infinity)
        put("thrust_ramp_s", () -> "2.5");  // LITERAL
    }

    private static void put(String k, Supplier<String> v) { N.put(k, v); }

    static String pct(double d) { return Math.round(d * 100) + "%"; }
    static String pct1(double d) {
        double v = d * 100.0;
        return (Math.abs(v - Math.round(v)) < 0.05 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v)) + "%";
    }
    static String num(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d); }

    public static String get(String key) {
        Supplier<String> s = N.get(key);
        if (s == null) return "[?" + key + "]";
        try { return s.get(); } catch (Throwable t) { return "[?" + key + "]"; }
    }

    /** Replaces {n:key} and {key:id} tokens. Leaves {C} (crystal icon) alone. */
    public static String resolve(String text) {
        if (text == null || text.indexOf('{') < 0) return text;
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int o = text.indexOf('{', i);
            if (o < 0) { out.append(text, i, text.length()); break; }
            int c = text.indexOf('}', o);
            if (c < 0) { out.append(text, i, text.length()); break; }
            out.append(text, i, o);
            String tok = text.substring(o + 1, c);
            if (tok.startsWith("n:")) out.append(get(tok.substring(2)));
            else if (tok.startsWith("key:")) out.append(keyName(tok.substring(4)));
            else out.append('{').append(tok).append('}');
            i = c + 1;
        }
        return out.toString();
    }

    private static String keyName(String id) {
        String n = Info.keyName.apply(id);
        if (n != null) return "[" + n + "]";
        return switch (id) {
            case "flight_thrust" -> "[Left Ctrl]";
            default -> "the key you set in Controls";
        };
    }

    private GuideNumbers() {}
}
