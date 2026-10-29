package dev.justin.infinium.client;

import dev.justin.infinium.CrystalCharge;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumArmorTier;
import dev.justin.infinium.InfiniumDurability;
import dev.justin.infinium.InfiniumSwordItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Energy-mode sword cooldown meter (2026-10-29): bigger, bolder and purple, and PERMANENT. Unlike vanilla's
 * attack indicator it never hides when full: it stays (glowing) until the next swing empties it.
 * During the post-stab twirl lockout it shows the lockout instead of the vanilla attack strength.
 */
public final class SwordCooldownHud {
    private SwordCooldownHud() {}
    static final int W = 48, H = 5, BORDER = 1, BELOW_CROSSHAIR = 14;
    static final int BG = 0xC0140A1E, EDGE = 0xFF3A1257, FILL = 0xFF9B3DFF, FILL_HI = 0xFFC77BFF, FULL = 0xFFD98CFF, FULL_GLOW = 0x55B04CFF;

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || p.isSpectator()) return;
        ItemStack held = p.getMainHandItem();
        if (!held.is(Infinium.SWORD) || InfiniumDurability.disabled(held) || !InfiniumSwordItem.isEnergyMode(held)) return;
        if (InfiniumArmorTier.getTier(p) < 1 || !CrystalCharge.saberUnlocked(p)) return;

        float prog = Math.min(p.getAttackStrengthScale(0F), SwordSlashClientHandler.lockProgress(p));
        boolean full = prog >= 0.99F;
        int x = g.guiWidth() / 2 - W / 2, y = g.guiHeight() / 2 + BELOW_CROSSHAIR;
        if (full) g.fill(x - 2, y - 2, x + W + 2, y + H + 2, FULL_GLOW);
        g.fill(x - BORDER, y - BORDER, x + W + BORDER, y + H + BORDER, EDGE);
        g.fill(x, y, x + W, y + H, BG);
        int fw = Math.round(W * Math.max(0F, Math.min(1F, prog)));
        if (fw > 0) {
            g.fill(x, y, x + fw, y + H, full ? FULL : FILL);
            g.fill(x, y, x + fw, y + 1, full ? 0xFFF0C8FF : FILL_HI);   // top highlight = bolder look
        }
    }
}
