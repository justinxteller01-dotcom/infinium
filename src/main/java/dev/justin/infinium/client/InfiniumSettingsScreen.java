package dev.justin.infinium.client;

import dev.justin.infinium.Infinium;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/** Visual & Sound Settings: Grouped armor particles (3-way: On, Third Person Only, Off), world effects, UI, sounds, controls. */
public class InfiniumSettingsScreen extends OptionsSubScreen {
    private static boolean openNext;
    private boolean listening;
    private Button keyButton;
    private CycleButton<Boolean> gesture;

    public enum ParticleMode {
        ON("On", ChatFormatting.GREEN, "Visible in both third and first person."),
        THIRD_PERSON_ONLY("3rd Person Only", ChatFormatting.YELLOW, "Visible in third person only."),
        OFF("Off", ChatFormatting.RED, "Disabled completely.");

        public final String label;
        public final ChatFormatting format;
        public final String desc;
        ParticleMode(String label, ChatFormatting format, String desc) {
            this.label = label;
            this.format = format;
            this.desc = desc;
        }
    }

    public InfiniumSettingsScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.literal("Infinium Visual & Sound Settings"));
    }

    public static void registerEntryPoints() {
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("infinium")
            .then(ClientCommands.literal("settings").executes(c -> { openNext = true; return 1; }))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (openNext) { openNext = false; mc.gui.setScreen(new InfiniumSettingsScreen(mc.gui.screen())); }
        });
        InventorySettingsButton.register();
    }

    private static Component cap(String s) { return Component.literal(s); }

    private static ParticleMode particleMode(String cat) {
        boolean w = InfiniumClientConfig.show(cat, false);
        boolean fp = InfiniumClientConfig.show(cat, true);
        if (!w) return ParticleMode.OFF;
        if (!fp) return ParticleMode.THIRD_PERSON_ONLY;
        return ParticleMode.ON;
    }

    private static void setParticleMode(String cat, ParticleMode mode) {
        switch (mode) {
            case ON -> {
                InfiniumClientConfig.setShow(cat, false, true);
                InfiniumClientConfig.setShow(cat, true, true);
            }
            case THIRD_PERSON_ONLY -> {
                InfiniumClientConfig.setShow(cat, false, true);
                InfiniumClientConfig.setShow(cat, true, false);
            }
            case OFF -> {
                InfiniumClientConfig.setShow(cat, false, false);
                InfiniumClientConfig.setShow(cat, true, false);
            }
        }
    }

    private CycleButton<ParticleMode> triToggle(String label, String cat, String desc) {
        ParticleMode initial = particleMode(cat);
        return CycleButton.<ParticleMode>builder(m -> Component.literal(m.label).withStyle(m.format), initial)
            .withValues(ParticleMode.ON, ParticleMode.THIRD_PERSON_ONLY, ParticleMode.OFF)
            .withTooltip(v -> Tooltip.create(Component.literal(desc + "\n" + v.desc)))
            .create(Component.literal(label), (b, v) -> setParticleMode(cat, v));
    }

    @Override
    protected void addOptions() {
        // Top navigation: Perk settings & prominent Reset to defaults
        list.addSmall(
            Button.builder(Component.literal("< Perk Settings"), b -> minecraft.gui.setScreen(new InfiniumPerksScreen(lastScreen))).build(),
            Button.builder(Component.literal("Reset to Defaults").withStyle(ChatFormatting.RED), b -> {
                InfiniumClientConfig.defaults();
                InfiniumClientConfig.save();
                InfiniumClientConfig.sendSwordTheme();
                minecraft.gui.setScreen(new InfiniumSettingsScreen(lastScreen));
            }).build()
        );
        list.addSmall(
            Button.builder(Component.translatable("gui.infinium.settings.open_guide"),
                b -> minecraft.gui.setScreen(new dev.justin.infinium.client.guide.FlatGuideScreen(this))).build(),
            (AbstractWidget) null
        );

        // ---- Armor Particles Grouped by Style ----
        String[] famKeys = {"astral", "cybernetic", "singularity", "cyberpsycho", "infinity"};
        String[] famNames = {"Astral", "Cybernetic", "Singularity", "Cyber Psycho", "Infinity"};
        for (int i = 0; i < 5; i++) {
            String fk = famKeys[i];
            String fn = famNames[i];
            list.addHeader(Component.literal(fn + " Particles").withStyle(ChatFormatting.LIGHT_PURPLE));
            CycleButton<ParticleMode> arm = triToggle("Armor", "armor." + fk, fn + " armor ambient particle aura.");
            CycleButton<ParticleMode> wpn = triToggle("Weapon", "weapon." + fk, fn + " tool & weapon particle effects.");
            list.addSmall(arm, wpn);
        }

        list.addHeader(Component.literal("General Particles & HUD").withStyle(ChatFormatting.LIGHT_PURPLE));
        CycleButton<Boolean> dye = toggle("Dye Aura", InfiniumClientConfig.particle("dye_aura.world"), v -> {
            InfiniumClientConfig.setParticle("dye_aura.world", v);
            InfiniumClientConfig.setParticle("dye_aura.first_person", v);
        });
        dye.setTooltip(Tooltip.create(Component.literal("Dyed armor subtle colored energy aura.")));
        CycleButton<Boolean> sw = toggle("Set Switch Burst", InfiniumClientConfig.particle("set_switch.world"), v -> {
            InfiniumClientConfig.setParticle("set_switch.world", v);
            InfiniumClientConfig.setParticle("set_switch.first_person", v);
        });
        sw.setTooltip(Tooltip.create(Component.literal("Burst effect when switching full armor sets.")));
        list.addSmall(dye, sw);

        CycleButton<Boolean> frag = toggle("Fragments & Crystals", InfiniumClientConfig.particle("fragments.world"), v -> {
            InfiniumClientConfig.setParticle("fragments.world", v);
            InfiniumClientConfig.setParticle("fragments.first_person", v);
        });
        frag.setTooltip(Tooltip.create(Component.literal("Ground sparkles near dropped spacetime fragments.")));
        CycleButton<Boolean> hud = toggle("Inventory HUD", InfiniumClientConfig.hudTheme(), InfiniumClientConfig::setHudTheme);
        hud.setTooltip(Tooltip.create(Component.literal("Set-colored theme border and energy effects inside player inventory.")));
        list.addSmall(frag, hud);

        // ---- World Effects & UI ----
        list.addHeader(Component.literal("World & UI Effects").withStyle(ChatFormatting.LIGHT_PURPLE));
        CycleButton<Boolean> ripple = toggle("Daytime Sky Ripple", InfiniumClientConfig.skyRipple(), InfiniumClientConfig::setSkyRipple);
        ripple.setTooltip(Tooltip.create(Component.literal("Periodic cosmic spacetime ripples expanding across the daytime sky.")));
        CycleButton<Boolean> stars = toggle("Shooting Stars", InfiniumClientConfig.shootingStars(), InfiniumClientConfig::setShootingStars);
        stars.setTooltip(Tooltip.create(Component.literal("Purple shooting stars streaking across the night sky.")));
        list.addSmall(ripple, stars);

        CycleButton<Boolean> shimmer = toggle("Underwater Shimmer", InfiniumClientConfig.underwaterShimmer(), InfiniumClientConfig::setUnderwaterShimmer);
        shimmer.setTooltip(Tooltip.create(Component.literal("A faint purple cosmic shimmer while underwater.")));
        CycleButton<Boolean> uiFx = toggle("UI Cosmic Effects", InfiniumClientConfig.uiEffects(), InfiniumClientConfig::setUiEffects);
        uiFx.setTooltip(Tooltip.create(Component.literal("Animated deep space cosmic backgrounds and progressive container infection effects.")));
        list.addSmall(shimmer, uiFx);

        // ---- Sounds ----
        String[] kindNames = {"Pickup", "Place", "Equip", "Full Set"};
        int[] order = {0, 1, 3, 2, 4};   // Astral, Cybernetic, Cyber Psycho, Singularity, Infinity
        for (int f : order) {
            String fam = InfiniumClientConfig.FAMILIES[f], name = dev.justin.infinium.Perk.FAMILY_NAMES[f];
            String[] tips = {
                "Play a sound effect when picking up " + name + " gear from an inventory slot.",
                "Play a sound effect when placing " + name + " gear in an inventory slot.",
                "Play a sound effect when equipping a " + name + " armor piece.",
                "Play a sound effect when completing a full " + name + " set."};
            list.addHeader(Component.literal(name + " Sounds").withStyle(ChatFormatting.LIGHT_PURPLE));
            List<AbstractWidget> row = new ArrayList<>();
            for (int k = 0; k < 4; k++) {
                String key = fam + "." + InfiniumClientConfig.KINDS[k];
                CycleButton<Boolean> t = toggle(kindNames[k], InfiniumClientConfig.sound(key), v -> InfiniumClientConfig.setSound(key, v));
                t.setTooltip(Tooltip.create(Component.literal(tips[k])));
                row.add(t);
                if (row.size() == 2) { list.addSmall(row.get(0), row.get(1)); row.clear(); }
            }
        }
        list.addHeader(Component.literal("Tool Sounds").withStyle(ChatFormatting.LIGHT_PURPLE));
        CycleButton<Boolean> tp = toggle("Pickup", InfiniumClientConfig.sound("tools.pickup"), v -> InfiniumClientConfig.setSound("tools.pickup", v));
        tp.setTooltip(Tooltip.create(Component.literal("Play sound when picking up an Infinium tool from an inventory slot.")));
        CycleButton<Boolean> tl = toggle("Place", InfiniumClientConfig.sound("tools.place"), v -> InfiniumClientConfig.setSound("tools.place", v));
        tl.setTooltip(Tooltip.create(Component.literal("Play sound when placing an Infinium tool in an inventory slot.")));
        list.addSmall(tp, tl);

        // ---- Sword Sounds ----
        list.addHeader(Component.literal("Sword Sounds").withStyle(ChatFormatting.LIGHT_PURPLE));
        list.addBig(new StringWidget(Component.literal("Astral and Cybernetic sets always use their own sound.").withStyle(ChatFormatting.GRAY), font));
        CycleButton<Integer> singBtn = swordThemeButton("Singularity Sword",
            "Chooses between Singularity and Astral sound themes while wearing a full Singularity set.",
            InfiniumClientConfig.swordThemeSingularity(),
            List.of(2, 0), InfiniumClientConfig::setSwordThemeSingularity);
        CycleButton<Integer> psychoBtn = swordThemeButton("Cyberpsycho Sword",
            "Chooses between Cyberpsycho and Cybernetic sound themes while wearing a full Cyberpsycho set.",
            InfiniumClientConfig.swordThemeCyberpsycho(),
            List.of(3, 1), InfiniumClientConfig::setSwordThemeCyberpsycho);
        list.addSmall(singBtn, psychoBtn);

        CycleButton<Integer> infBtn = swordThemeButton("Infinity Sword",
            "Chooses between Infinity, Astral, Cybernetic, Singularity, and Cyberpsycho sound themes while wearing a full Infinity set.",
            InfiniumClientConfig.swordThemeInfinity(),
            List.of(4, 0, 1, 2, 3), InfiniumClientConfig::setSwordThemeInfinity);
        list.addSmall(infBtn, (AbstractWidget) null);

        // ---- Controls ----
        list.addHeader(Component.translatable("gui.infinium.settings.controls").withStyle(ChatFormatting.LIGHT_PURPLE));
        list.addBig(new StringWidget(Component.literal("Wings toggle: Shift + Right Click on air (default)."), font));
        keyButton = Button.builder(keyLabel(), b -> { listening = true; b.setMessage(Component.literal("> press a key <").withStyle(ChatFormatting.YELLOW)); }).build();
        keyButton.setTooltip(Tooltip.create(Component.literal("Click, then press a key. Escape cancels, Backspace/Delete unbinds.")));
        boolean unbound = InfiniumKeys.TOGGLE_WINGS == null || InfiniumKeys.TOGGLE_WINGS.isUnbound();
        gesture = CycleButton.onOffBuilder(InfiniumClientConfig.wingsGesture())
            .create(Component.literal("Shift + right-click toggles wings"), (b, v) -> InfiniumClientConfig.setWingsGesture(v));
        gesture.active = !unbound;
        if (unbound) gesture.setTooltip(Tooltip.create(Component.literal("Always on while no custom key is set")));
        list.addSmall(keyButton, gesture);
        // batch1 C3: ability keys (X-Ray Pulse, Quantum Tunnel) and flight thrust live in vanilla Controls.
        Button controls = Button.builder(Component.translatable("gui.infinium.settings.open_controls"),
            b -> dev.justin.infinium.client.ability.BindKeyHandler.openControls(minecraft, this)).build();
        controls.setTooltip(Tooltip.create(Component.literal("Opens Options > Controls > Key Binds at the Infinium section.")));
        list.addBig(controls);
    }

    private Component keyLabel() {
        var k = InfiniumKeys.TOGGLE_WINGS;
        return Component.literal("Custom key: ").append(k == null || k.isUnbound() ? Component.literal("Not set").withStyle(ChatFormatting.GRAY) : k.getTranslatedKeyMessage());
    }

    private CycleButton<Boolean> toggle(String label, boolean value, java.util.function.Consumer<Boolean> set) {
        return CycleButton.onOffBuilder(value).create(cap(label), (b, v) -> set.accept(v));
    }

    private static Component swordThemeName(int ordinal) {
        return switch (ordinal) {
            case 0 -> Component.literal("Astral");
            case 1 -> Component.literal("Cybernetic");
            case 2 -> Component.literal("Singularity");
            case 3 -> Component.literal("Cyberpsycho");
            case 4 -> Component.literal("Infinity");
            default -> Component.literal("Default");
        };
    }

    private CycleButton<Integer> swordThemeButton(String label, String tooltip, int initial, List<Integer> values, java.util.function.Consumer<Integer> setter) {
        return CycleButton.<Integer>builder(InfiniumSettingsScreen::swordThemeName, initial)
            .withValues(values)
            .withTooltip(v -> Tooltip.create(Component.literal(tooltip)))
            .create(Component.literal(label), (b, v) -> {
                setter.accept(v);
                InfiniumClientConfig.save();
                InfiniumClientConfig.sendSwordTheme();
            });
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (listening && InfiniumKeys.TOGGLE_WINGS != null) {
            listening = false;
            if (e.key() == InputConstants.KEY_ESCAPE) { keyButton.setMessage(keyLabel()); return true; }
            if (e.key() == InputConstants.KEY_BACKSPACE || e.key() == InputConstants.KEY_DELETE) InfiniumKeys.TOGGLE_WINGS.setKey(InputConstants.UNKNOWN);
            else InfiniumKeys.TOGGLE_WINGS.setKey(InputConstants.getKey(e));
            net.minecraft.client.KeyMapping.resetMapping();
            options.save();
            keyButton.setMessage(keyLabel());
            boolean unbound = InfiniumKeys.TOGGLE_WINGS.isUnbound();
            if (gesture != null) gesture.active = !unbound;
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void removed() {
        InfiniumClientConfig.save();
        super.removed();
    }
}
