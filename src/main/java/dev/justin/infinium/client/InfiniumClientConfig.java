package dev.justin.infinium.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import dev.justin.infinium.SetLook;
import dev.justin.infinium.SwordTheme;
import dev.justin.infinium.SwordThemePayload;

/** Client-only options (13.8 / 13.10 / 13.15): config/infinium-client.json, version 1. Missing keys = true, unknown keys ignored. */
public final class InfiniumClientConfig {
    public static final String[] FAMILIES = {"astral", "cybernetic", "singularity", "cyberpsycho", "infinity"};
    public static final String[] KINDS = {"pickup", "place", "equip", "fullset"};
    /** category id, label. Each category has .world and .first_person, default true. */
    public static final String[][] PARTICLE_CATS = {
            {"armor.astral", "Astral armor"}, {"weapon.astral", "Astral weapons"},
            {"armor.cybernetic", "Cybernetic armor"}, {"weapon.cybernetic", "Cybernetic weapons"},
            {"armor.singularity", "Singularity armor"}, {"weapon.singularity", "Singularity weapons"},
            {"armor.cyberpsycho", "Cyber Psycho armor"}, {"weapon.cyberpsycho", "Cyber Psycho weapons"},
            {"armor.infinity", "Infinity armor"}, {"weapon.infinity", "Infinity weapons"},
            {"dye_aura", "Dye aura"}, {"set_switch", "Set switch"}, {"fragments", "Fragments and crystal"}
    };

    /** "astral.pickup" .. "tools.place" */
    private static final Map<String, Boolean> SOUNDS = new LinkedHashMap<>();
    private static final Map<String, Boolean> PART = new LinkedHashMap<>();
    private static boolean gesture = true;
    private static boolean hudTheme = true;
    private static boolean shootingStars = true;
    private static boolean uiEffects = true;
    private static boolean skyRipple = true;
    /** sky-v7: where shooting stars/ripples render. AUTO = sky pass (textured, survives Iris packs), auto-fallback to WORLD if the hook is missing. */
    public enum SkyFxMode { AUTO, SKY, WORLD }
    private static SkyFxMode skyFxMode = SkyFxMode.AUTO;
    private static boolean underwaterShimmer = true;
    private static float meteorDensity = 1.0f;
    private static float daytimeSkyRippleDensity = 1.0f;
    private static float underwaterParticleDensity = 1.0f;
    private static float wingScaleMultiplier = 1.0f;
    private static float wingOpacityMultiplier = 1.0f;
    private static float particleDensityMultiplier = 1.0f;
    private static float soundVolumeMaster = 1.0f;
    private static float soundVolumeEquip = 1.0f;
    private static float soundVolumeMeteor = 1.0f;
    private static float soundVolumeCrystal = 1.0f;
    private static int swordThemeSingularity = 2;
    private static int swordThemeCyberpsycho = 3;
    private static int swordThemeInfinity = 4;
    private static final Set<Identifier> MUTED = new HashSet<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static { defaults(); }
    public static void defaults() {
        SOUNDS.clear(); PART.clear();
        for (String f : FAMILIES) for (String k : KINDS) SOUNDS.put(f + "." + k, true);
        SOUNDS.put("tools.pickup", true); SOUNDS.put("tools.place", true);
        for (String[] c : PARTICLE_CATS) { PART.put(c[0] + ".world", true); PART.put(c[0] + ".first_person", true); }
        gesture = true;
        hudTheme = true;
        shootingStars = true;
        uiEffects = true;
        skyRipple = true;
        skyFxMode = SkyFxMode.AUTO;
        underwaterShimmer = true;
        meteorDensity = 1.0f;
        daytimeSkyRippleDensity = 1.0f;
        underwaterParticleDensity = 1.0f;
        wingScaleMultiplier = 1.0f;
        wingOpacityMultiplier = 1.0f;
        particleDensityMultiplier = 1.0f;
        soundVolumeMaster = 1.0f;
        soundVolumeEquip = 1.0f;
        soundVolumeMeteor = 1.0f;
        soundVolumeCrystal = 1.0f;
        swordThemeSingularity = 2;
        swordThemeCyberpsycho = 3;
        swordThemeInfinity = 4;
        rebuild();
    }
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("infinium-client.json"); }

    public static void load() {
        defaults();
        try {
            Path f = file();
            if (!Files.exists(f)) { save(); return; }
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.get("sounds") instanceof JsonObject so) for (String key : SOUNDS.keySet()) {
                String[] k = key.split("\\.");
                if (so.get(k[0]) instanceof JsonObject fo && fo.get(k[1]) instanceof JsonElement e && e.isJsonPrimitive()) SOUNDS.put(key, e.getAsBoolean());
            }
            if (o.get("particles") instanceof JsonObject po) {
                for (String key : PART.keySet())
                    if (po.get(key) instanceof JsonElement e && e.isJsonPrimitive()) PART.put(key, e.getAsBoolean());
                migrate(po);
            }
            if (o.get("controls") instanceof JsonObject co && co.get("wings_gesture") instanceof JsonElement e && e.isJsonPrimitive()) gesture = e.getAsBoolean();
            if (o.get("hud_theme") instanceof JsonElement e && e.isJsonPrimitive()) hudTheme = e.getAsBoolean();
            if (o.get("wings") instanceof JsonObject wo) {
                if (wo.get("scale") instanceof JsonElement e && e.isJsonPrimitive()) wingScaleMultiplier = e.getAsFloat();
                if (wo.get("opacity") instanceof JsonElement e && e.isJsonPrimitive()) wingOpacityMultiplier = e.getAsFloat();
            }
            if (o.get("particle_density") instanceof JsonElement pde && pde.isJsonPrimitive()) particleDensityMultiplier = pde.getAsFloat();
            if (o.get("volumes") instanceof JsonObject vo) {
                if (vo.get("master") instanceof JsonElement e && e.isJsonPrimitive()) soundVolumeMaster = e.getAsFloat();
                if (vo.get("equip") instanceof JsonElement e && e.isJsonPrimitive()) soundVolumeEquip = e.getAsFloat();
                if (vo.get("meteor") instanceof JsonElement e && e.isJsonPrimitive()) soundVolumeMeteor = e.getAsFloat();
                if (vo.get("crystal") instanceof JsonElement e && e.isJsonPrimitive()) soundVolumeCrystal = e.getAsFloat();
            }
            if (o.get("sword_themes") instanceof JsonObject st) {
                if (st.get("singularity") instanceof JsonElement e && e.isJsonPrimitive()) setSwordThemeSingularity(e.getAsInt());
                if (st.get("cyberpsycho") instanceof JsonElement e && e.isJsonPrimitive()) setSwordThemeCyberpsycho(e.getAsInt());
                if (st.get("infinity") instanceof JsonElement e && e.isJsonPrimitive()) setSwordThemeInfinity(e.getAsInt());
            }
            if (o.get("sword_theme_singularity") instanceof JsonElement e && e.isJsonPrimitive()) setSwordThemeSingularity(e.getAsInt());
            if (o.get("sword_theme_cyberpsycho") instanceof JsonElement e && e.isJsonPrimitive()) setSwordThemeCyberpsycho(e.getAsInt());
            if (o.get("sword_theme_infinity") instanceof JsonElement e && e.isJsonPrimitive()) setSwordThemeInfinity(e.getAsInt());
            readWorldFx(o);
        } catch (Exception e) {
            dev.justin.infinium.Infinium.LOG.warn("infinium-client.json unreadable, using defaults: {}", e.toString());
        }
        rebuild();
    }

    public static void save() {
        JsonObject o = new JsonObject();
        o.addProperty("version", 1);
        JsonObject so = new JsonObject();
        for (var en : SOUNDS.entrySet()) {
            String[] k = en.getKey().split("\\.");
            if (!so.has(k[0])) so.add(k[0], new JsonObject());
            so.getAsJsonObject(k[0]).addProperty(k[1], en.getValue());
        }
        o.add("sounds", so);
        JsonObject po = new JsonObject();
        PART.forEach(po::addProperty);
        o.add("particles", po);
        o.addProperty("hud_theme", hudTheme);
        JsonObject fx = new JsonObject();
        fx.addProperty("shootingStars", shootingStars);
        fx.addProperty("meteorDensity", meteorDensity);
        fx.addProperty("uiEffects", uiEffects);
        fx.addProperty("skyRipple", skyRipple);
        fx.addProperty("skyFxMode", skyFxMode.name());
        fx.addProperty("daytimeSkyRippleDensity", daytimeSkyRippleDensity);
        fx.addProperty("underwaterShimmer", underwaterShimmer);
        fx.addProperty("underwaterParticleDensity", underwaterParticleDensity);
        o.add("worldFx", fx);
        JsonObject co = new JsonObject();
        co.addProperty("wings_gesture", gesture);
        o.add("controls", co);
        JsonObject wo = new JsonObject();
        wo.addProperty("scale", wingScaleMultiplier);
        wo.addProperty("opacity", wingOpacityMultiplier);
        o.add("wings", wo);
        o.addProperty("particle_density", particleDensityMultiplier);
        JsonObject vo = new JsonObject();
        vo.addProperty("master", soundVolumeMaster);
        vo.addProperty("equip", soundVolumeEquip);
        vo.addProperty("meteor", soundVolumeMeteor);
        vo.addProperty("crystal", soundVolumeCrystal);
        o.add("volumes", vo);
        JsonObject st = new JsonObject();
        st.addProperty("singularity", swordThemeSingularity);
        st.addProperty("cyberpsycho", swordThemeCyberpsycho);
        st.addProperty("infinity", swordThemeInfinity);
        o.add("sword_themes", st);
        try {
            Path f = file(), tmp = f.resolveSibling("infinium-client.json.tmp");
            Files.createDirectories(f.getParent());
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            try { Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (Exception atomicFail) { Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception e) {
            dev.justin.infinium.Infinium.LOG.warn("could not save infinium-client.json: {}", e.toString());
        }
    }

    private static void rebuild() {
        MUTED.clear();
        for (var en : SOUNDS.entrySet()) {
            if (en.getValue()) continue;
            String[] k = en.getKey().split("\\.");
            String fam = k[0].equals("tools") ? "tool" : k[0];
            String kind = k[1].equals("fullset") ? "set_equip" : k[1];
            MUTED.add(dev.justin.infinium.Infinium.id(fam + "_" + kind));
        }
    }

    /** Old flat keys (set_astral_armor, dye_aura, ...) apply to both sides when the new keys are absent. */
    private static void migrate(JsonObject po) {
        for (String f : FAMILIES) {
            take(po, "set_" + f + "_armor", "armor." + f);
            take(po, "set_" + f + "_weapon", "weapon." + f);
        }
        take(po, "dye_aura", "dye_aura");
        take(po, "set_switch_burst", "set_switch");
        take(po, "fragments_crystal", "fragments");
    }
    private static void take(JsonObject po, String old, String cat) {
        if (po.has(cat + ".world") || po.has(cat + ".first_person")) return;
        if (!(po.get(old) instanceof JsonElement e) || !e.isJsonPrimitive()) return;
        boolean v = e.getAsBoolean();
        PART.put(cat + ".world", v);
        PART.put(cat + ".first_person", v);
    }

    public static boolean sound(String key) { return SOUNDS.getOrDefault(key, true); }
    public static void setSound(String key, boolean on) { SOUNDS.put(key, on); rebuild(); }
    public static boolean muted(Identifier id) { return !MUTED.isEmpty() && MUTED.contains(id); }

    /** World toggle, or the local player's first-person toggle. Missing keys stay on. */
    public static boolean show(String cat, boolean firstPerson) { return particle(cat + (firstPerson ? ".first_person" : ".world")); }
    public static void setShow(String cat, boolean firstPerson, boolean on) { setParticle(cat + (firstPerson ? ".first_person" : ".world"), on); }
    public static boolean hudTheme() { return hudTheme; }
    public static void setHudTheme(boolean on) { hudTheme = on; }
    /** Installer keys: worldFx.shootingStars, worldFx.uiEffects, worldFx.skyRipple. Missing = true. */
    public static boolean shootingStars() { return shootingStars; }
    public static boolean uiEffects() { return uiEffects; }
    public static boolean skyRipple() { return skyRipple; }
    public static boolean underwaterShimmer() { return underwaterShimmer; }
    public static float meteorDensity() { return Math.max(0.0f, Math.min(2.0f, meteorDensity)); }
    public static float daytimeSkyRippleDensity() { return Math.max(0.0f, Math.min(2.0f, daytimeSkyRippleDensity)); }
    public static float underwaterParticleDensity() { return Math.max(0.0f, Math.min(3.0f, underwaterParticleDensity)); }
    public static void setShootingStars(boolean on) { shootingStars = on; }
    public static void setUiEffects(boolean on) { uiEffects = on; }
    public static void setSkyRipple(boolean on) { skyRipple = on; }
    public static SkyFxMode skyFxMode() { return skyFxMode; }
    public static void setSkyFxMode(SkyFxMode m) { skyFxMode = m == null ? SkyFxMode.AUTO : m; }
    public static void setUnderwaterShimmer(boolean on) { underwaterShimmer = on; }
    public static void setMeteorDensity(float v) { meteorDensity = Math.max(0.0f, Math.min(2.0f, v)); }
    public static void setDaytimeSkyRippleDensity(float v) { daytimeSkyRippleDensity = Math.max(0.0f, Math.min(2.0f, v)); }
    public static void setUnderwaterParticleDensity(float v) { underwaterParticleDensity = Math.max(0.0f, Math.min(3.0f, v)); }

    /** Nested object "worldFx", or a flat root key "worldFx.shootingStars" if an installer wrote it that way. */
    private static void readWorldFx(JsonObject o) {
        if (o.get("worldFx") instanceof JsonObject fx) {
            shootingStars = bool(fx, "shootingStars", shootingStars);
            meteorDensity = flt(fx, "meteorDensity", meteorDensity);
            uiEffects = bool(fx, "uiEffects", uiEffects);
            skyRipple = bool(fx, "skyRipple", skyRipple);
            if (fx.get("skyFxMode") instanceof JsonElement m && m.isJsonPrimitive()) {
                try { skyFxMode = SkyFxMode.valueOf(m.getAsString().toUpperCase(java.util.Locale.ROOT)); } catch (IllegalArgumentException ignored) {}
            }
            daytimeSkyRippleDensity = flt(fx, "daytimeSkyRippleDensity", daytimeSkyRippleDensity);
            underwaterShimmer = bool(fx, "underwaterShimmer", underwaterShimmer);
            underwaterParticleDensity = flt(fx, "underwaterParticleDensity", underwaterParticleDensity);
        }
        if (o.get("worldFx.shootingStars") instanceof JsonElement e && e.isJsonPrimitive()) shootingStars = e.getAsBoolean();
        if (o.get("worldFx.meteorDensity") instanceof JsonElement e && e.isJsonPrimitive()) meteorDensity = e.getAsFloat();
        if (o.get("worldFx.uiEffects") instanceof JsonElement e && e.isJsonPrimitive()) uiEffects = e.getAsBoolean();
        if (o.get("worldFx.skyRipple") instanceof JsonElement e && e.isJsonPrimitive()) skyRipple = e.getAsBoolean();
        if (o.get("worldFx.daytimeSkyRippleDensity") instanceof JsonElement e && e.isJsonPrimitive()) daytimeSkyRippleDensity = e.getAsFloat();
        if (o.get("worldFx.underwaterShimmer") instanceof JsonElement e && e.isJsonPrimitive()) underwaterShimmer = e.getAsBoolean();
        if (o.get("worldFx.underwaterParticleDensity") instanceof JsonElement e && e.isJsonPrimitive()) underwaterParticleDensity = e.getAsFloat();
    }
    private static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.get(key) instanceof JsonElement e && e.isJsonPrimitive() ? e.getAsBoolean() : fallback;
    }
    private static float flt(JsonObject o, String key, float fallback) {
        return o.get(key) instanceof JsonElement e && e.isJsonPrimitive() ? e.getAsFloat() : fallback;
    }
    /** R2 name, now the world half of the armor category. */
    public static boolean setArmorParticles(dev.justin.infinium.SetLook l) { return setArmorParticles(l, false); }
    public static boolean setArmorParticles(dev.justin.infinium.SetLook l, boolean firstPerson) { return l != null && show("armor." + l.id, firstPerson); }
    public static boolean setWeaponParticles(dev.justin.infinium.SetLook l) { return setWeaponParticles(l, false); }
    public static boolean setWeaponParticles(dev.justin.infinium.SetLook l, boolean firstPerson) { return l != null && show("weapon." + l.id, firstPerson); }
    public static boolean particle(String key) { return PART.getOrDefault(key, true); }
    public static void setParticle(String key, boolean on) { PART.put(key, on); }
    /** band 0 helmet .. 3 boots. Per-piece toggles were removed; the set toggle is the one that matters. */
    public static boolean armorParticles(int band) { return true; }
    public static boolean toolParticles(ItemStack s) {
        if (s.is(dev.justin.infinium.Infinium.SWORD)) return particle("sword");
        if (s.is(dev.justin.infinium.Infinium.PICKAXE)) return particle("pickaxe");
        if (s.is(dev.justin.infinium.Infinium.AXE)) return particle("axe");
        if (s.is(dev.justin.infinium.Infinium.SHOVEL)) return particle("shovel");
        if (s.is(dev.justin.infinium.Infinium.HOE)) return particle("hoe");
        return true;
    }

    /** 13.15 A: forced On while the custom key is unbound. */
    public static boolean wingsGesture() { return gesture || InfiniumKeys.TOGGLE_WINGS == null || InfiniumKeys.TOGGLE_WINGS.isUnbound(); }
    public static boolean wingsGestureRaw() { return gesture; }
    public static void setWingsGesture(boolean on) { gesture = on; }

    public static float wingScaleMultiplier() { return Math.max(0.5f, Math.min(2.0f, wingScaleMultiplier)); }
    public static void setWingScaleMultiplier(float v) { wingScaleMultiplier = Math.max(0.5f, Math.min(2.0f, v)); }

    public static float wingOpacityMultiplier() { return Math.max(0.2f, Math.min(1.5f, wingOpacityMultiplier)); }
    public static void setWingOpacityMultiplier(float v) { wingOpacityMultiplier = Math.max(0.2f, Math.min(1.5f, v)); }

    public static float particleDensityMultiplier() { return Math.max(0.0f, Math.min(2.5f, particleDensityMultiplier)); }
    public static void setParticleDensityMultiplier(float v) { particleDensityMultiplier = Math.max(0.0f, Math.min(2.5f, v)); }

    public static float soundVolumeMaster() { return Math.max(0.0f, Math.min(1.0f, soundVolumeMaster)); }
    public static void setSoundVolumeMaster(float v) { soundVolumeMaster = Math.max(0.0f, Math.min(1.0f, v)); }

    public static float soundVolumeEquip() { return Math.max(0.0f, Math.min(1.0f, soundVolumeEquip)); }
    public static void setSoundVolumeEquip(float v) { soundVolumeEquip = Math.max(0.0f, Math.min(1.0f, v)); }

    public static float soundVolumeMeteor() { return Math.max(0.0f, Math.min(1.0f, soundVolumeMeteor)); }
    public static void setSoundVolumeMeteor(float v) { soundVolumeMeteor = Math.max(0.0f, Math.min(1.0f, v)); }

    public static float soundVolumeCrystal() { return Math.max(0.0f, Math.min(1.0f, soundVolumeCrystal)); }
    public static void setSoundVolumeCrystal(float v) { soundVolumeCrystal = Math.max(0.0f, Math.min(1.0f, v)); }

    public static int swordThemeSingularity() { return swordThemeSingularity; }
    public static int getSwordThemeSingularity() { return swordThemeSingularity; }
    public static void setSwordThemeSingularity(int val) {
        swordThemeSingularity = SwordTheme.allowed(SetLook.SINGULARITY, val) ? val : 2;
    }

    public static int swordThemeCyberpsycho() { return swordThemeCyberpsycho; }
    public static int getSwordThemeCyberpsycho() { return swordThemeCyberpsycho; }
    public static void setSwordThemeCyberpsycho(int val) {
        swordThemeCyberpsycho = SwordTheme.allowed(SetLook.CYBERPSYCHO, val) ? val : 3;
    }

    public static int swordThemeInfinity() { return swordThemeInfinity; }
    public static int getSwordThemeInfinity() { return swordThemeInfinity; }
    public static void setSwordThemeInfinity(int val) {
        swordThemeInfinity = SwordTheme.allowed(SetLook.INFINITY, val) ? val : 4;
    }

    public static void sendSwordTheme() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            SwordTheme.set(mc.player.getUUID(), new byte[]{0, 1, (byte) swordThemeSingularity, (byte) swordThemeCyberpsycho, (byte) swordThemeInfinity});
        }
        if (ClientPlayNetworking.canSend(SwordThemePayload.TYPE)) {
            ClientPlayNetworking.send(new SwordThemePayload(
                (byte) swordThemeSingularity,
                (byte) swordThemeCyberpsycho,
                (byte) swordThemeInfinity
            ));
        }
    }

    private InfiniumClientConfig() {}
}
