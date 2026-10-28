package dev.justin.infinium.client;

import dev.justin.infinium.InfiniumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Standard Fabric Mod Menu configuration screen for Infinium.
 * Exposes all numeric gameplay and visual configuration options across:
 * - Meteor Shower event frequency, damage, loot rates, and spawn radius
 * - Crystal slot capacities for all armor tiers
 * - Durability floor & rocket-free elytra flight speeds
 * - Combat & speed perk values
 * - Utility perks (repair intervals, block saving chance, reach distance)
 * - Wings scale, opacity, and particle density multipliers
 * - Audio volume controls for master, equip, meteor, and crystal sounds
 */
public class InfiniumModMenuConfigScreen extends OptionsSubScreen {

    public InfiniumModMenuConfigScreen(Screen lastScreen) {
        super(lastScreen, Minecraft.getInstance().options, Component.translatable("text.infinium.config.title"));
    }

    @Override
    protected void addOptions() {
        if (this.list == null) return;
        boolean remote = isRemote();

        // =========================================================================
        // CLIENT-SIDE VISUAL & AUDIO SETTINGS (Presented first for casual players)
        // =========================================================================

        // --- 1. Wings & Armor Visuals (Client) ---
        this.list.addHeader(Component.translatable("text.infinium.config.category.visuals"));
        List<AbstractWidget> visuals = new ArrayList<>();
        visuals.add(new NumericSlider(Component.literal("Wing Size Multiplier"), "x", 0.50, 2.00, 0.05, 2, InfiniumClientConfig.wingScaleMultiplier(), v -> InfiniumClientConfig.setWingScaleMultiplier((float) v)));
        visuals.add(new NumericSlider(Component.literal("Wing Opacity Multiplier"), "x", 0.20, 1.50, 0.05, 2, InfiniumClientConfig.wingOpacityMultiplier(), v -> InfiniumClientConfig.setWingOpacityMultiplier((float) v)));
        visuals.add(new NumericSlider(Component.literal("Armor Particle Density"), "x", 0.00, 2.50, 0.05, 2, InfiniumClientConfig.particleDensityMultiplier(), v -> InfiniumClientConfig.setParticleDensityMultiplier((float) v)));
        visuals.add(makeToggle(Component.literal("UI Crystal Particles"), InfiniumClientConfig.uiEffects(), InfiniumClientConfig::setUiEffects));
        visuals.add(makeToggle(Component.literal("Dark HUD Theme"), InfiniumClientConfig.hudTheme(), InfiniumClientConfig::setHudTheme));
        this.list.addSmall(visuals);

        // --- 2. World Visual Effects (Client) ---
        this.list.addHeader(Component.translatable("text.infinium.config.category.world_fx"));
        List<AbstractWidget> worldFx = new ArrayList<>();
        worldFx.add(makeToggle(
            Component.translatable("text.infinium.config.world_fx.shooting_stars"),
            InfiniumClientConfig.shootingStars(),
            InfiniumClientConfig::setShootingStars,
            Component.translatable("text.infinium.config.world_fx.shooting_stars.tooltip")
        ));
        worldFx.add(new NumericSlider(
            Component.translatable("text.infinium.config.world_fx.meteor_density"),
            "%", 0, 200, 5, 0,
            InfiniumClientConfig.meteorDensity() * 100.0,
            v -> InfiniumClientConfig.setMeteorDensity((float) (v / 100.0))
        ).withTooltip(Component.translatable("text.infinium.config.world_fx.meteor_density.tooltip")));

        worldFx.add(makeToggle(
            Component.translatable("text.infinium.config.world_fx.sky_ripple"),
            InfiniumClientConfig.skyRipple(),
            InfiniumClientConfig::setSkyRipple,
            Component.translatable("text.infinium.config.world_fx.sky_ripple.tooltip")
        ));
        worldFx.add(new NumericSlider(
            Component.translatable("text.infinium.config.world_fx.sky_ripple_density"),
            "%", 0, 200, 5, 0,
            InfiniumClientConfig.daytimeSkyRippleDensity() * 100.0,
            v -> InfiniumClientConfig.setDaytimeSkyRippleDensity((float) (v / 100.0))
        ).withTooltip(Component.translatable("text.infinium.config.world_fx.sky_ripple_density.tooltip")));

        worldFx.add(makeToggle(
            Component.translatable("text.infinium.config.world_fx.underwater_shimmer"),
            InfiniumClientConfig.underwaterShimmer(),
            InfiniumClientConfig::setUnderwaterShimmer,
            Component.translatable("text.infinium.config.world_fx.underwater_shimmer.tooltip")
        ));
        worldFx.add(new NumericSlider(
            Component.translatable("text.infinium.config.world_fx.underwater_density"),
            "%", 0, 300, 10, 0,
            InfiniumClientConfig.underwaterParticleDensity() * 100.0,
            v -> InfiniumClientConfig.setUnderwaterParticleDensity((float) (v / 100.0))
        ).withTooltip(Component.translatable("text.infinium.config.world_fx.underwater_density.tooltip")));
        this.list.addSmall(worldFx);

        // --- 3. Audio Volumes (Client) ---
        this.list.addHeader(Component.translatable("text.infinium.config.category.audio"));
        List<AbstractWidget> volumes = new ArrayList<>();
        volumes.add(new NumericSlider(Component.literal("Master Sound Volume"), "%", 0, 100, 5, 0, InfiniumClientConfig.soundVolumeMaster() * 100.0, v -> InfiniumClientConfig.setSoundVolumeMaster((float) (v / 100.0))));
        volumes.add(new NumericSlider(Component.literal("Armor Equip Volume"), "%", 0, 100, 5, 0, InfiniumClientConfig.soundVolumeEquip() * 100.0, v -> InfiniumClientConfig.setSoundVolumeEquip((float) (v / 100.0))));
        volumes.add(new NumericSlider(Component.literal("Meteor Shower Volume"), "%", 0, 100, 5, 0, InfiniumClientConfig.soundVolumeMeteor() * 100.0, v -> InfiniumClientConfig.setSoundVolumeMeteor((float) (v / 100.0))));
        volumes.add(new NumericSlider(Component.literal("Crystal Matrix Volume"), "%", 0, 100, 5, 0, InfiniumClientConfig.soundVolumeCrystal() * 100.0, v -> InfiniumClientConfig.setSoundVolumeCrystal((float) (v / 100.0))));
        this.list.addSmall(volumes);

        // =========================================================================
        // SERVER-SIDE GAMEPLAY & CONFIGURATION
        // =========================================================================

        // --- 4. Meteor Shower Event (Server) ---
        this.list.addHeader(Component.translatable("text.infinium.config.category.meteors"));
        List<AbstractWidget> meteor = new ArrayList<>();
        meteor.add(new NumericSlider(Component.literal("Shower Event Chance"), "%", 0, 200, 5, 0, InfiniumConfig.getMeteorShowerChance() * 100.0, v -> InfiniumConfig.setMeteorShowerChance((float) (v / 100.0)))
            .withTooltip(Component.literal("Overall frequency modifier for meteor shower events. 0% disables showers entirely, 100% is standard (every 10-15 days), 200% doubles frequency.")));
        meteor.add(new NumericSlider(Component.literal("Min Days Between"), " days", 1, 60, 1, 0, InfiniumConfig.getMeteorMinDays(), v -> InfiniumConfig.setMeteorMinDays((int) v)));
        meteor.add(new NumericSlider(Component.literal("Max Days Between"), " days", 1, 60, 1, 0, InfiniumConfig.getMeteorMaxDays(), v -> InfiniumConfig.setMeteorMaxDays((int) v)));
        meteor.add(new NumericSlider(Component.literal("Direct Impact Damage"), " hearts", 0.0, 20.0, 0.5, 1, InfiniumConfig.getMeteorDirectDamage(), v -> InfiniumConfig.setMeteorDirectDamage((float) v)));
        meteor.add(new NumericSlider(Component.literal("Loot Drop Chance"), "%", 0.0, 50.0, 0.5, 1, InfiniumConfig.getMeteorLootChance() * 100.0, v -> InfiniumConfig.setMeteorLootChance((float) (v / 100.0))));
        meteor.add(new NumericSlider(Component.literal("Min Fragments / Meteor"), "", 0, 16, 1, 0, InfiniumConfig.getMeteorMinFragments(), v -> InfiniumConfig.setMeteorMinFragments((int) v)));
        meteor.add(new NumericSlider(Component.literal("Max Fragments / Meteor"), "", 1, 32, 1, 0, InfiniumConfig.getMeteorMaxFragments(), v -> InfiniumConfig.setMeteorMaxFragments((int) v)));
        meteor.add(new NumericSlider(Component.literal("T1 Template Drop Rate"), "%", 0.0, 5.0, 0.1, 1, InfiniumConfig.getMeteorTier1TemplateChance() * 100.0, v -> InfiniumConfig.setMeteorTier1TemplateChance((float) (v / 100.0))));
        meteor.add(new NumericSlider(Component.literal("T2 Template Drop Rate"), "%", 0.0, 5.0, 0.1, 1, InfiniumConfig.getMeteorTier2TemplateChance() * 100.0, v -> InfiniumConfig.setMeteorTier2TemplateChance((float) (v / 100.0))));
        meteor.add(new NumericSlider(Component.literal("Spawn Bubble Radius"), "m", 32, 320, 16, 0, InfiniumConfig.getMeteorBubbleRadius(), v -> InfiniumConfig.setMeteorBubbleRadius((int) v)));
        addServerWidgets(meteor, remote);

        // --- 5. Chest Loot & Templates (Server) ---
        this.list.addHeader(Component.translatable("text.infinium.config.category.loot_and_templates"));
        List<AbstractWidget> lootAndTemplates = new ArrayList<>();
        lootAndTemplates.add(new NumericSlider(Component.literal("World Chest Fragments"), "%", 0, 300, 10, 0, InfiniumConfig.getWorldChestFragmentRate() * 100.0, v -> InfiniumConfig.setWorldChestFragmentRate(v / 100.0))
            .withTooltip(Component.literal("Spacetime fragment spawn rate in natural world chests. 0% disables fragments, 100% is standard, 300% triples fragment drop rates.")));
        lootAndTemplates.add(new NumericSlider(Component.literal("Dungeon Chest Fragments"), "%", 0, 300, 10, 0, InfiniumConfig.getDungeonChestFragmentRate() * 100.0, v -> InfiniumConfig.setDungeonChestFragmentRate(v / 100.0))
            .withTooltip(Component.literal("Spacetime fragment spawn rate in Roguelike Dungeon containers. 0% disables fragments, 100% is standard, 300% triples fragment budget.")));
        lootAndTemplates.add(new NumericSlider(Component.literal("Astral Template Rate"), "%", 0, 300, 10, 0, InfiniumConfig.getTemplateRateAstral() * 100.0, v -> InfiniumConfig.setTemplateRateAstral(v / 100.0))
            .withTooltip(Component.literal("Relative spawn rate and weight of Astral Upgrade Smithing Templates across all world chests, dungeons, and meteors. 0% prevents drops.")));
        lootAndTemplates.add(new NumericSlider(Component.literal("Cybernetic Template Rate"), "%", 0, 300, 10, 0, InfiniumConfig.getTemplateRateCybernetic() * 100.0, v -> InfiniumConfig.setTemplateRateCybernetic(v / 100.0))
            .withTooltip(Component.literal("Relative spawn rate and weight of Cybernetic Upgrade Smithing Templates across all world chests, dungeons, and meteors. 0% prevents drops.")));
        lootAndTemplates.add(new NumericSlider(Component.literal("Singularity Template Rate"), "%", 0, 300, 10, 0, InfiniumConfig.getTemplateRateSingularity() * 100.0, v -> InfiniumConfig.setTemplateRateSingularity(v / 100.0))
            .withTooltip(Component.literal("Relative spawn rate and weight of Singularity Upgrade Smithing Templates across all world chests, dungeons, and meteors. 0% prevents drops.")));
        lootAndTemplates.add(new NumericSlider(Component.literal("CyberPsycho Template Rate"), "%", 0, 300, 10, 0, InfiniumConfig.getTemplateRateCyberpsycho() * 100.0, v -> InfiniumConfig.setTemplateRateCyberpsycho(v / 100.0))
            .withTooltip(Component.literal("Relative spawn rate and weight of Cyber Psycho Upgrade Smithing Templates across all world chests, dungeons, and meteors. 0% prevents drops.")));
        addServerWidgets(lootAndTemplates, remote);

        // --- 6. Crystals & Capacities (Server) ---
        this.list.addHeader(Component.translatable("text.infinium.config.category.crystals"));
        List<AbstractWidget> crystals = new ArrayList<>();
        addServerWidgets(crystals, remote);

        // --- 7. Core Gameplay & Flight (Server) ---
        this.list.addHeader(Component.translatable("text.infinium.config.category.gameplay"));
        List<AbstractWidget> gameplay = new ArrayList<>();
        gameplay.add(new NumericSlider(Component.literal("Gear Durability Floor"), "", 1, 200, 5, 0, InfiniumConfig.getDurabilityFloor(), v -> InfiniumConfig.setDurabilityFloor((int) v)));
        gameplay.add(new NumericSlider(Component.literal("Flight Ramp Up Time"), "s", 0.5, 8.0, 0.25, 2, InfiniumConfig.getFlightRampSeconds(), InfiniumConfig::setFlightRampSeconds));
        addServerWidgets(gameplay, remote);

        // --- 8. Combat & Utility Perks (Server) ---
        this.list.addHeader(Component.translatable("text.infinium.config.category.combat"));
        List<AbstractWidget> combat = new ArrayList<>();
        combat.add(new NumericSlider(Component.literal("T1 Mining Speed Bonus"), "%", 0, 100, 5, 0, InfiniumConfig.getMiningBonusT1() * 100.0, v -> InfiniumConfig.setMiningBonusT1(v / 100.0)));
        combat.add(new NumericSlider(Component.literal("T2 Mining Speed Bonus"), "%", 0, 150, 5, 0, InfiniumConfig.getMiningBonusT2() * 100.0, v -> InfiniumConfig.setMiningBonusT2(v / 100.0)));
        combat.add(new NumericSlider(Component.literal("T3 Mining Speed Bonus"), "%", 0, 200, 5, 0, InfiniumConfig.getMiningBonusT3() * 100.0, v -> InfiniumConfig.setMiningBonusT3(v / 100.0)));
        combat.add(new NumericSlider(Component.literal("T1 Attack/Bow Bonus"), "%", 0, 100, 5, 0, InfiniumConfig.getAttackBonusT1() * 100.0, v -> InfiniumConfig.setAttackBonusT1(v / 100.0)));
        combat.add(new NumericSlider(Component.literal("T2 Attack/Bow Bonus"), "%", 0, 200, 5, 0, InfiniumConfig.getAttackBonusT2() * 100.0, v -> InfiniumConfig.setAttackBonusT2(v / 100.0)));
        combat.add(new NumericSlider(Component.literal("T3 Attack/Bow Bonus"), "%", 0, 300, 5, 0, InfiniumConfig.getAttackBonusT3() * 100.0, v -> InfiniumConfig.setAttackBonusT3(v / 100.0)));
        combat.add(new NumericSlider(Component.literal("T1 Mob Slow Aura"), "%", 0, 100, 5, 0, InfiniumConfig.getSlowAuraT1() * 100.0, v -> InfiniumConfig.setSlowAuraT1(v / 100.0)));
        combat.add(new NumericSlider(Component.literal("T2 Mob Slow Aura"), "%", 0, 100, 5, 0, InfiniumConfig.getSlowAuraT2() * 100.0, v -> InfiniumConfig.setSlowAuraT2(v / 100.0)));
        combat.add(new NumericSlider(Component.literal("T3 Mob Slow Aura"), "%", 0, 100, 5, 0, InfiniumConfig.getSlowAuraT3() * 100.0, v -> InfiniumConfig.setSlowAuraT3(v / 100.0)));
        combat.add(new NumericSlider(Component.literal("Mob Slow Radius"), "m", 4, 48, 2, 0, InfiniumConfig.getSlowRadius(), v -> InfiniumConfig.setSlowRadius((int) v)));
        combat.add(new NumericSlider(Component.literal("Astral Leg Speed"), "%", 0, 50, 1, 0, InfiniumConfig.getLegSpeedAstral() * 100.0, v -> InfiniumConfig.setLegSpeedAstral(v / 100.0)));
        combat.add(new NumericSlider(Component.literal("Cybernetic Leg Speed"), "%", 0, 50, 1, 0, InfiniumConfig.getLegSpeedCybernetic() * 100.0, v -> InfiniumConfig.setLegSpeedCybernetic(v / 100.0)));
        combat.add(new NumericSlider(Component.literal("CyberPsycho Leg Speed"), "%", 0, 50, 1, 0, InfiniumConfig.getLegSpeedCyberpsycho() * 100.0, v -> InfiniumConfig.setLegSpeedCyberpsycho(v / 100.0)));
        combat.add(new NumericSlider(Component.literal("Infinity Leg Speed"), "%", 0, 50, 1, 0, InfiniumConfig.getLegSpeedInfinity() * 100.0, v -> InfiniumConfig.setLegSpeedInfinity(v / 100.0)));
        combat.add(new NumericSlider(Component.literal("T1 Damage Taken"), "%", 10, 100, 1, 1, InfiniumConfig.getDamageTakenT1() * 100.0, v -> InfiniumConfig.setDamageTakenT1(v / 100.0)));
        combat.add(new NumericSlider(Component.literal("T2 Damage Taken"), "%", 10, 100, 1, 1, InfiniumConfig.getDamageTakenT2() * 100.0, v -> InfiniumConfig.setDamageTakenT2(v / 100.0)));
        combat.add(new NumericSlider(Component.literal("T3 Damage Taken"), "%", 10, 100, 1, 1, InfiniumConfig.getDamageTakenT3() * 100.0, v -> InfiniumConfig.setDamageTakenT3(v / 100.0)));
        addServerWidgets(combat, remote);

        this.list.addHeader(Component.translatable("text.infinium.config.category.utility"));
        List<AbstractWidget> utility = new ArrayList<>();
        utility.add(new NumericSlider(Component.literal("T1 Auto-Repair Interval"), "s", 1, 60, 1, 0, InfiniumConfig.getRepairIntervalT1(), v -> InfiniumConfig.setRepairIntervalT1((int) v)));
        utility.add(new NumericSlider(Component.literal("T2 Auto-Repair Interval"), "s", 1, 60, 1, 0, InfiniumConfig.getRepairIntervalT2(), v -> InfiniumConfig.setRepairIntervalT2((int) v)));
        utility.add(new NumericSlider(Component.literal("T3 Auto-Repair Interval"), "s", 1, 60, 1, 0, InfiniumConfig.getRepairIntervalT3(), v -> InfiniumConfig.setRepairIntervalT3((int) v)));
        utility.add(new NumericSlider(Component.literal("T1 Block Saver Chance"), "%", 0, 100, 1, 0, InfiniumConfig.getKeepChanceT1() * 100.0, v -> InfiniumConfig.setKeepChanceT1(v / 100.0)));
        utility.add(new NumericSlider(Component.literal("T2 Block Saver Chance"), "%", 0, 100, 1, 0, InfiniumConfig.getKeepChanceT2() * 100.0, v -> InfiniumConfig.setKeepChanceT2(v / 100.0)));
        utility.add(new NumericSlider(Component.literal("T3 Block Saver Chance"), "%", 0, 100, 1, 0, InfiniumConfig.getKeepChanceT3() * 100.0, v -> InfiniumConfig.setKeepChanceT3(v / 100.0)));
        utility.add(new NumericSlider(Component.literal("T1 Block Reach Bonus"), " blocks", 0.0, 8.0, 0.5, 1, InfiniumConfig.getReachBonusT1(), InfiniumConfig::setReachBonusT1));
        utility.add(new NumericSlider(Component.literal("T2 Block Reach Bonus"), " blocks", 0.0, 8.0, 0.5, 1, InfiniumConfig.getReachBonusT2(), InfiniumConfig::setReachBonusT2));
        utility.add(new NumericSlider(Component.literal("T3 Block Reach Bonus"), " blocks", 0.0, 12.0, 0.5, 1, InfiniumConfig.getReachBonusT3(), InfiniumConfig::setReachBonusT3));
        addServerWidgets(utility, remote);

        // --- 9. Experimental / Advanced (may break game) ---
        this.list.addHeader(Component.translatable("text.infinium.config.category.experimental"));
        List<AbstractWidget> experimental = new ArrayList<>();
        experimental.add(new NumericSlider(Component.literal("Dungeon Reset Empty Time"), " min", 5, 120, 5, 0, InfiniumConfig.getDungeonEmptyMinutes(), v -> InfiniumConfig.setDungeonEmptyMinutes((int) v))
            .withTooltip(Component.literal("(Experimental: may break game) Server uptime minutes a dungeon must remain completely empty of players before resetting structure & chests.")));
        experimental.add(new NumericSlider(Component.literal("Chest Refill Min Days"), " real days", 1, 14, 1, 0, InfiniumConfig.getChestRefillMinDays(), v -> InfiniumConfig.setChestRefillMinDays((int) v))
            .withTooltip(Component.literal("(Experimental: may break game) Minimum real-world wall-clock days before an opened natural chest restocks loot.")));
        experimental.add(new NumericSlider(Component.literal("Chest Refill Max Days"), " real days", 1, 14, 1, 0, InfiniumConfig.getChestRefillMaxDays(), v -> InfiniumConfig.setChestRefillMaxDays((int) v))
            .withTooltip(Component.literal("(Experimental: may break game) Maximum real-world wall-clock days before an opened natural chest restocks loot.")));
        addServerWidgets(experimental, remote);
    }

    private void addServerWidgets(List<AbstractWidget> widgets, boolean remote) {
        if (remote) {
            for (AbstractWidget w : widgets) {
                w.active = false;
            }
        }
        this.list.addSmall(widgets);
    }

    private boolean isRemote() {
        return this.minecraft != null && this.minecraft.level != null && !this.minecraft.hasSingleplayerServer();
    }

    @Override
    protected void addFooter() {
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(Component.literal("Reset Defaults"), b -> {
            if (!isRemote()) {
                InfiniumConfig.resetDefaults();
                InfiniumConfig.save();
            }
            InfiniumClientConfig.defaults();
            InfiniumClientConfig.save();
            this.rebuildWidgets();
        }).width(140).build());
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).width(140).build());
    }

    /** Rebuild only the list entries in place. Vanilla's rebuildWidgets re-runs init(), which adds a second OptionsList
     *  (and title/footer) to the final HeaderAndFooterLayout, so the stale list stayed drawn on top as a frozen copy. */
    @Override
    protected void rebuildWidgets() {
        if (list == null) { super.rebuildWidgets(); return; }
        double scroll = list.scrollAmount();
        list.setFocused(null);
        list.replaceEntries(java.util.List.of());
        addOptions();
        list.setScrollAmount(scroll);
    }

    @Override
    public void onClose() {
        if (!isRemote()) {
            InfiniumConfig.save();
        }
        InfiniumClientConfig.save();
        super.onClose();
    }

    private static AbstractWidget makeToggle(Component label, boolean current, Consumer<Boolean> onToggle) {
        return makeToggle(label, current, onToggle, null);
    }

    private static AbstractWidget makeToggle(Component label, boolean current, Consumer<Boolean> onToggle, Component tooltip) {
        AbstractWidget btn = Button.builder(Component.empty().append(label).append(Component.literal(": " + (current ? "ON" : "OFF"))), b -> {
            boolean next = !b.getMessage().getString().endsWith("ON");
            b.setMessage(Component.empty().append(label).append(Component.literal(": " + (next ? "ON" : "OFF"))));
            onToggle.accept(next);
        }).width(150).build();
        if (tooltip != null) {
            btn.setTooltip(Tooltip.create(tooltip));
        }
        return btn;
    }

    /**
     * Standard discrete numeric slider button.
     */
    public static class NumericSlider extends AbstractSliderButton {
        private final Component prefix;
        private final String suffix;
        private final double min;
        private final double max;
        private final double step;
        private final int decimals;
        private final DoubleConsumer onChange;

        public NumericSlider(Component prefix, String suffix, double min, double max, double step, int decimals, double current, DoubleConsumer onChange) {
            super(0, 0, 150, 20, Component.empty(), (clamp(current, min, max) - min) / (max - min));
            this.prefix = prefix;
            this.suffix = suffix;
            this.min = min;
            this.max = max;
            this.step = step;
            this.decimals = decimals;
            this.onChange = onChange;
            this.updateMessage();
        }

        public NumericSlider withTooltip(Component tooltip) {
            if (tooltip != null) {
                this.setTooltip(Tooltip.create(tooltip));
            }
            return this;
        }

        private static double clamp(double v, double min, double max) {
            return Math.max(min, Math.min(max, v));
        }

        public double getActualValue() {
            double raw = min + this.value * (max - min);
            if (step > 0) {
                raw = Math.round((raw - min) / step) * step + min;
            }
            return clamp(raw, min, max);
        }

        @Override
        protected void updateMessage() {
            double val = getActualValue();
            String formatted = decimals == 0
                    ? String.valueOf((long) Math.round(val))
                    : String.format(Locale.ROOT, "%." + decimals + "f", val);
            this.setMessage(Component.empty().append(prefix).append(Component.literal(": " + formatted + suffix)));
        }

        @Override
        protected void applyValue() {
            updateMessage();
            if (onChange != null) {
                onChange.accept(getActualValue());
            }
        }
    }
}
