package dev.justin.infinium.client;

import dev.justin.infinium.CrystalCharge;
import dev.justin.infinium.SetLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Progressive Infinium inventory HUD effects (#20, #30).
 * Keeps the player inventory darkness level untouched while adding subtle cosmic motes,
 * hairline crack pulses, and rim energy that scale up as the player's armor crystal energy increases.
 */
public final class InventoryHudTheme {
    public static void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, SetLook look) {
        if (!InfiniumClientConfig.uiEffects()) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        SetLook effectiveLook = look != null ? look : HolderStyleProperty.wornLook(player);
        if (effectiveLook == null && !InventorySettingsButton.hasGear(player)) return;
        float progress = CrystalCharge.visualProgress(player);
        UiInfection.panelFx(g, w, h, progress, effectiveLook);
    }

    private InventoryHudTheme() {}
}
