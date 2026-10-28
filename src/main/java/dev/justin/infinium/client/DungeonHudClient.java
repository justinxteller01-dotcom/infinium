package dev.justin.infinium.client;

import dev.justin.infinium.dungeon.DungeonHudPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Renders on-screen minimalistic HUD text showing chest reset time remaining
 * when a player is inside dungeon chunks (at any Y level, including the surface).
 */
public final class DungeonHudClient {
    private static boolean active = false;
    private static String statusText = "";

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DungeonHudPayload.TYPE, (payload, ctx) -> {
            active = payload.inDungeon();
            statusText = payload.timeRemaining();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> {
            active = false;
            statusText = "";
        });
    }

    public static boolean isActive() {
        return active && !statusText.isEmpty();
    }

    public static void render(GuiGraphicsExtractor g) {
        if (!active || statusText.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (mc.gui != null && mc.gui.hud != null && mc.gui.hud.isHidden())) return;

        var font = mc.font;
        if (font == null) return;

        // Minimalistic, clean aesthetic in the upper-left of screen
        String prefix = "✦ Dungeon Loot: ";
        int prefixCol = 0xFFD270FF; // Cosmic purple
        int valCol = statusText.equalsIgnoreCase("Ready") ? 0xFFFF7CD3 : 0xFFEDE0F5; // pink if ready (was mint green), silver/white if counting

        int x = 8;
        int y = 8;

        // Subtle dark translucent backing pill for readability against bright backgrounds
        int tw = font.width(prefix + statusText);
        g.fill(x - 3, y - 2, x + tw + 3, y + font.lineHeight + 2, 0x8812061A);
        g.fill(x - 4, y - 2, x - 3, y + font.lineHeight + 2, prefixCol);

        g.text(font, prefix, x, y, prefixCol, false);
        g.text(font, statusText, x + font.width(prefix), y, valCol, false);
    }

    private DungeonHudClient() {}
}
