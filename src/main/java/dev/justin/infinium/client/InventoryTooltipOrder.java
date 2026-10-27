package dev.justin.infinium.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * G4: item tooltips over the Accessories inventory button.
 * Cause: vanilla draws tooltips in Screen.extractRenderStateWithTooltipAndSubtitles -> extractDeferredElements,
 * but owo-lib's Layer (which hosts the Accessories button) draws in Fabric's ScreenEvents.afterExtract, which
 * fires after that whole method, so the button lands on top of the tooltip. On the inventory screens we hold the
 * deferred pass and run it once afterExtract listeners are done.
 */
public final class InventoryTooltipOrder {
    private static boolean active;
    private static boolean held;
    private static int hx, hy;
    private static float ha;

    public static boolean applies(Screen s) {
        return s instanceof InventoryScreen || s instanceof CreativeModeInventoryScreen;
    }

    public static void begin(boolean defer) {
        active = defer;
        held = false;
    }

    /** @return true if the deferred pass was held (caller must skip it). */
    public static boolean hold(int mx, int my, float a) {
        if (!active) return false;
        held = true;
        hx = mx; hy = my; ha = a;
        return true;
    }

    public static void end(GuiGraphicsExtractor g) {
        boolean run = held;
        active = false;
        held = false;
        if (run) g.extractDeferredElements(hx, hy, ha);
    }

    private InventoryTooltipOrder() {}
}
