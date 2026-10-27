package dev.justin.infinium.client;

import dev.justin.infinium.HelmetVisionPayload;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumDurability;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Full Bright toggle (M3: available on EVERY Infinium helmet, per Justin's part3 answer 1).
 * The old continuous X-ray toggle + background scanner were removed in M3; X-ray is now the pulse ability
 * (dev.justin.infinium.ability.XrayPulse / client.ability.XrayPulseClient).
 */
public final class HelmetVisionClient {
    private static boolean fullBrightActive = false;

    public static boolean isFullBrightActive() {
        return fullBrightActive;
    }

    public static boolean isFullBrightEligible(ItemStack helmet) {
        return !helmet.isEmpty() && Infinium.isHelmet(helmet) && !InfiniumDurability.disabled(helmet);
    }

    public static void toggleFullBright(Player player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!isFullBrightEligible(head)) {
            Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal("Full Bright requires an Infinium Helmet").withStyle(ChatFormatting.RED), false);
            return;
        }
        fullBrightActive = !fullBrightActive;
        Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal("Mode: Full Bright " + (fullBrightActive ? "On" : "Off")).withStyle(fullBrightActive ? ChatFormatting.YELLOW : ChatFormatting.GRAY), false);
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, fullBrightActive ? 1.2F : 0.8F));
        if (ClientPlayNetworking.canSend(HelmetVisionPayload.TYPE)) {
            ClientPlayNetworking.send(new HelmetVisionPayload((byte) 1, fullBrightActive));
        }
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;
            if (fullBrightActive && !isFullBrightEligible(mc.player.getItemBySlot(EquipmentSlot.HEAD))) {
                fullBrightActive = false;   // graceful deactivation on unequip or durability failure
                if (ClientPlayNetworking.canSend(HelmetVisionPayload.TYPE)) {
                    ClientPlayNetworking.send(new HelmetVisionPayload((byte) 1, false));
                }
            }
        });
    }

    private HelmetVisionClient() {}
}
