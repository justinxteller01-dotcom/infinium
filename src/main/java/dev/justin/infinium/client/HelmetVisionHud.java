package dev.justin.infinium.client;

import dev.justin.infinium.Infinium;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Heads-Up Display (HUD) overlay for active helmet vision modes.
 *
 * Collision Resolution for composite helmets (Infinity Helmet):
 * When both Full Bright and X-Ray vision are concurrently active,
 * pills render in separate, non-overlapping side-by-side positions
 * with a distinct 6px padding margin:
 *   [✦ Full Bright]  (6px gap)  [✦ X-Ray (48m)]
 *
 * Automatically offsets below the Dungeon Loot HUD if active.
 */
public final class HelmetVisionHud {

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (mc.gui != null && mc.gui.hud != null && mc.gui.hud.isHidden())) {
            return;
        }

        Font font = mc.font;
        if (font == null) return;

        ItemStack head = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        if (head.isEmpty() || !Infinium.isHelmet(head) || !Infinium.setWorn(mc.player)) return;   // M4: full set only

        boolean fbActive = HelmetVisionClient.isFullBrightActive() && HelmetVisionClient.isFullBrightEligible(head);

        if (!fbActive) return;

        int startY = DungeonHudClient.isActive() ? 24 : 8;
        int currentX = 8;
        int margin = 6;

        if (fbActive) {
            String fbText = "✦ Full Bright";
            int fbColor = 0xFFFFD700; // Bright gold
            int tw = font.width(fbText);
            int pillWidth = tw + 8;

            // Translucent dark background pill with gold left border accent
            g.fill(currentX, startY - 2, currentX + pillWidth, startY + font.lineHeight + 2, 0x8812061A);
            g.fill(currentX, startY - 2, currentX + 2, startY + font.lineHeight + 2, fbColor);
            g.text(font, fbText, currentX + 5, startY, fbColor, false);

        }

    }

    private HelmetVisionHud() {}
}
