package dev.justin.infinium.client;

import dev.justin.infinium.Infinium;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public class InfiniumClient implements ClientModInitializer {
    static final int WRAP_PX = 280;
    /** Split one styled line at ~200 px; continuation lines get a 2-space indent. Styles of every segment are kept. */
    static java.util.List<net.minecraft.network.chat.Component> wrap(Minecraft mc, net.minecraft.network.chat.Component c) {
        var font = mc.font;
        if (font == null || font.width(c) <= WRAP_PX) return java.util.List.of(c);
        java.util.List<net.minecraft.network.chat.Component> out = new java.util.ArrayList<>();
        for (var ft : font.getSplitter().splitLines(c, WRAP_PX, net.minecraft.network.chat.Style.EMPTY)) {
            net.minecraft.network.chat.MutableComponent line = net.minecraft.network.chat.Component.literal(out.isEmpty() ? "" : "  ");
            ft.visit((style, str) -> { line.append(net.minecraft.network.chat.Component.literal(str).withStyle(style)); return java.util.Optional.empty(); },
                net.minecraft.network.chat.Style.EMPTY);
            out.add(line);
        }
        return out;
    }

    private static final int[] PSYCHO_COLS = {0xB04CFF, 0xD24CFF, 0xFF4CE0, 0xFF7AD9, 0x9B3BFF};   // CyberPsycho purple / magenta / pink

    @Override
    public void onInitializeClient() {
        DarkUi.register();
        AirJumpClient.register();   // G4b
        // G4b: runtime tooltips (Info) show the actually bound key.
        dev.justin.infinium.Info.keyName = id -> {
            net.minecraft.client.KeyMapping k = switch (id) {
                case "flight_thrust" -> InfiniumKeys.ABILITY_CONTROL;
                case "toggle_fullbright" -> InfiniumKeys.TOGGLE_FULLBRIGHT;
                case "toggle_wings" -> InfiniumKeys.TOGGLE_WINGS;
                case "warp" -> dev.justin.infinium.SpacetimeWarp.ABILITY == null ? null : dev.justin.infinium.client.ability.AbilityClient.key(dev.justin.infinium.SpacetimeWarp.ABILITY);
                case "xray_pulse" -> dev.justin.infinium.ability.XrayPulse.ABILITY == null ? null : dev.justin.infinium.client.ability.AbilityClient.key(dev.justin.infinium.ability.XrayPulse.ABILITY);
                default -> null;
            };
            if (k == null) return null;
            return k.isUnbound() ? "its key (unbound, set it in Controls)" : k.getTranslatedKeyMessage().getString();
        };
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(dev.justin.infinium.Infinium.ENERGY_SLASH, EnergySlashRenderer::new);
        try {
            Class<?> ch = Class.forName("win.demistorm.visual_health.ConfigHelper");
            Object inst = ch.getField("INSTANCE").get(null);
            if (inst != null) ch.getField("damagePlayers").setBoolean(inst, false);
        } catch (Throwable ignored) {}
        // v4: full-bright shooting-star particles
        net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance().register(dev.justin.infinium.Infinium.STAR_HEAD,
                (net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.PendingParticleProvider<net.minecraft.core.particles.SimpleParticleType>) sprites -> (t, lvl, x, y, z, dx, dy, dz, r) -> new StarParticle(lvl, x, y, z, sprites, true));
        net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance().register(dev.justin.infinium.Infinium.STAR_TRAIL,
                (net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.PendingParticleProvider<net.minecraft.core.particles.SimpleParticleType>) sprites -> (t, lvl, x, y, z, dx, dy, dz, r) -> new StarParticle(lvl, x, y, z, sprites, false));   // pass2: built-in dark container textures
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(dev.justin.infinium.ModeFx.Payload.TYPE, (pl, ctx) -> {
            net.minecraft.client.Minecraft mc = ctx.client();
            if (mc.player == null || mc.level == null) return;
            if (pl.kind() == dev.justin.infinium.ModeFx.WINGS) {
                net.minecraft.world.item.ItemStack ch = mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
                if (Infinium.isChestplate(ch)) {
                    if (pl.mode() == 1) ch.set(net.minecraft.core.component.DataComponents.GLIDER, net.minecraft.util.Unit.INSTANCE);
                    else {
                        ch.remove(net.minecraft.core.component.DataComponents.GLIDER);
                        if (mc.player.isFallFlying()) mc.player.stopFallFlying();
                    }
                }
            }
            boolean fp = mc.options.getCameraType().isFirstPerson();
            if (fp && pl.kind() == dev.justin.infinium.ModeFx.WINGS) return;   // wing burst never in first person
            var d = dev.justin.infinium.ModeFx.dust(pl.rgb());
            var pts = fp ? dev.justin.infinium.ModeFx.firstPerson(pl.kind(), pl.mode(), mc.player.getEyePosition(), mc.player.getYRot(), mc.player.getXRot())
                         : dev.justin.infinium.ModeFx.points(pl.kind(), pl.mode(), mc.player.position(), mc.player.getYRot());
            for (int i = 0; i < pts.size(); i += 2) { var v = pts.get(i); var w = pts.get(i + 1);
                mc.level.addParticle(d, v.x, v.y, v.z, w.x, w.y, w.z); }
        });
        DungeonBeams.register();
        WingThrustTrail.register();   // batch1: Cyber Psycho thrust afterglow ribbon
        dev.justin.infinium.client.mixin.SelectItemModelPropertiesAccessor.infinium$idMapper().put(Infinium.id("holder_style"), HolderStyleProperty.TYPE);
        dev.justin.infinium.client.mixin.SelectItemModelPropertiesAccessor.infinium$idMapper().put(Infinium.id("preview_color"), PreviewColorProperty.TYPE);
        // tooltips: style line, "Wings: On/Off" on the chestplate, then the "Hold Shift for more" section (same text as JEI)
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (lines.isEmpty()) return;
            int at = 1;
            if (dev.justin.infinium.InfiniumDurability.disabled(stack))   // 13.5: first line under the name
                lines.add(at++, net.minecraft.network.chat.Component.translatable(Infinium.isArmor(stack) ? "tooltip.infinium.broken" : "tooltip.infinium.broken_tool").withStyle(net.minecraft.ChatFormatting.RED));
            if (Infinium.isChestplate(stack))
                lines.add(at++, net.minecraft.network.chat.Component.literal("Wings: " + (stack.has(net.minecraft.core.component.DataComponents.GLIDER) ? "On" : "Off"))
                    .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
            Minecraft mc = Minecraft.getInstance();
            var det = dev.justin.infinium.Info.details(stack, mc.player == null ? 0L : dev.justin.infinium.Perks.mask(mc.player));
            if (det.isEmpty()) return;
            boolean shift = mc.hasShiftDown() || (mc.getWindow() != null
                && (com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), 340 /* GLFW_KEY_LEFT_SHIFT */)
                    || com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), 344 /* GLFW_KEY_RIGHT_SHIFT */)));
            if (shift) for (var c : det) lines.addAll(wrap(mc, c));   // ~200 px wrap (section 10)
            else lines.add(net.minecraft.network.chat.Component.literal("Hold Shift for more").withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC));
        });
        InfiniumClientConfig.load();
        dev.justin.infinium.InfiniumConfig.load();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(dev.justin.infinium.InfiniumConfigSyncPayload.TYPE, (pl, ctx) -> {
            dev.justin.infinium.InfiniumConfig.loadFromJson(pl.json());
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            InfiniumClientConfig.sendSwordTheme();
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            dev.justin.infinium.InfiniumConfig.load();
        });
        InfiniumKeys.register();
        dev.justin.infinium.client.ability.AbilityClient.register();   // M1: per-ability key mappings (unbound), HUD order, cooldown sync
        dev.justin.infinium.client.ability.BindKeyHandler.register();
        dev.justin.infinium.client.ability.XrayPulseClient.register();   // M3
        InfinityFlightClient.register();   // M6: remote thrust state
        ClientPerks.register();   // M5: one source of truth for perk/ability toggles
        InfiniumSettingsScreen.registerEntryPoints();
        ChestHintClient.register();
        DungeonHudClient.register();
        HelmetVisionClient.register();
        InventoryHelmetVisionButtons.register();
        MeteorShowerClient.register();
        ClientTickEvents.END_LEVEL_TICK.register(level -> {
            Minecraft mc = Minecraft.getInstance();
            if (!mc.isPaused()) {
                ArmorParticles.tick(mc, level);
                WorldSkyFx.tick(mc, level);
                MeteorShowerClient.tick(mc, level);
                InfinityFlightClient.clientTick(mc);
                SwordSlashClientHandler.clientTick(mc);
            }
        });
    }
}
