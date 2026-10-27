package dev.justin.infinium.client.ability;

import dev.justin.infinium.ability.Ability;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * HUD ability bar, bottom of the screen right of the hotbar ("middle-bottom-right").
 *
 * Per icon: faded copy (mostly transparent) + a clockwise clock-hand sweep from 12 o'clock that redraws the
 * icon at full opacity behind the hand (per-pixel angle mask in icon texture space, merged into horizontal
 * runs, so ~2 blits per texture row), a bold countdown number in the centre, the bound key under the icon
 * (overlapping its bottom edge), and a "Bind key" pill above it while unbound.
 */
public final class AbilityHud {
    public static final int ICON = 20;          // on-screen icon size (gui px)
    static final int GAP = 4;
    static final int FADED_ALPHA = 0x48;       // "mostly transparent"
    static final int NUMBER_COLOR = 0xE6FFFFFF; // "bold, mostly opaque"
    static final Component BIND = Component.translatable("hud.infinium.ability.bind_key");

    /** One laid-out slot (gui coords). bind* is the "Bind key" pill, width 0 if the key is bound. */
    public record Slot(Ability ability, int x, int y, int bindX, int bindY, int bindW, int bindH) {
        boolean bindHit(double mx, double my) { return bindW > 0 && mx >= bindX && mx < bindX + bindW && my >= bindY && my < bindY + bindH; }
    }

    public static List<Slot> layout(Minecraft mc, Player pl) {
        List<Slot> out = new ArrayList<>();
        if (pl == null || pl.isSpectator()) return out;
        // M4 (bug 5): no Infinium HUD at all until a full set is worn; this also hides the Bind-key pill.
        if (!dev.justin.infinium.Infinium.setWorn(pl)) return out;
        Font font = mc.font;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int x = w / 2 + 91 + 30;    // clear of the hotbar (182 wide) and the right-side offhand / attack indicator
        int y = h - ICON - 12;      // leaves room for the key label under the icon
        int bindH = font.lineHeight + 3;
        for (Ability a : AbilityClient.visible(pl)) {
            KeyMapping k = AbilityClient.key(a);
            boolean unbound = k == null || k.isUnbound();
            int bindW = unbound ? font.width(BIND) + 6 : 0;
            int slotW = Math.max(ICON, bindW);
            int ix = x + (slotW - ICON) / 2;
            out.add(new Slot(a, ix, y, x + (slotW - bindW) / 2, y - bindH - 2, bindW, bindH));
            x += slotW + GAP;
        }
        return out;
    }

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (mc.gui != null && mc.gui.hud != null && mc.gui.hud.isHidden())) return;
        Font font = mc.font;
        for (Slot s : layout(mc, mc.player)) {
            Ability a = s.ability();
            float prog = AbilityClient.progress(a.id());
            g.fill(s.x() - 1, s.y() - 1, s.x() + ICON + 1, s.y() + ICON + 1, 0x8812061A);   // dark backing square
            if (prog >= 1F) {
                icon(g, s, 0xFFFFFFFF);
            } else {
                icon(g, s, (FADED_ALPHA << 24) | 0xFFFFFF);
                sweep(g, s, prog);
                int secs = (int) Math.ceil(AbilityClient.remainingTicks(a.id()) / 20F);
                if (secs > 0) {
                    Component n = Component.literal(Integer.toString(secs)).withStyle(ChatFormatting.BOLD);
                    g.text(font, n, s.x() + (ICON - font.width(n)) / 2 + 1, s.y() + (ICON - font.lineHeight) / 2 + 1, NUMBER_COLOR, true);
                }
            }
            KeyMapping k = AbilityClient.key(a);
            if (k != null && !k.isUnbound()) {
                String label = font.plainSubstrByWidth(shortKey(k.getTranslatedKeyMessage().getString()), ICON + GAP + 6);
                int lw = font.width(label);
                int lx = s.x() + (ICON - lw) / 2, ly = s.y() + ICON - 2;   // slightly overlaps the icon's bottom edge
                g.fill(lx - 2, ly - 1, lx + lw + 1, ly + font.lineHeight, 0xB012061A);
                g.text(font, label, lx, ly, 0xFFE6D2FF, true);
            } else if (s.bindW() > 0) {
                boolean hover = BindKeyHandler.hovered(s);
                g.fill(s.bindX(), s.bindY(), s.bindX() + s.bindW(), s.bindY() + s.bindH(), hover ? 0xE07A3FD0 : 0xC03A1466);
                g.fill(s.bindX(), s.bindY() + s.bindH() - 1, s.bindX() + s.bindW(), s.bindY() + s.bindH(), 0xFFB07CFF);
                g.text(font, BIND, s.bindX() + 3, s.bindY() + 2, 0xFFFFFFFF, true);
            }
        }
    }

    private static void icon(GuiGraphicsExtractor g, Slot s, int argb) {
        int n = Ability.ICON_TEX_SIZE;
        g.pose().pushMatrix();
        g.pose().translate(s.x(), s.y());
        g.pose().scale(ICON / (float) n, ICON / (float) n);
        g.blit(RenderPipelines.GUI_TEXTURED, s.ability().icon(), 0, 0, 0F, 0F, n, n, n, n, argb);
        g.pose().popMatrix();
    }

    /**
     * Clock-hand reveal. Works in icon texture pixels (32x32) under a scale transform so every masked run is a
     * 1:1 texel blit. A texel is "restored" when its centre's clockwise angle from 12 o'clock is below
     * prog * 360deg. Each row is split into maximal runs of restored texels (at most 2 per row for a sector).
     */
    private static void sweep(GuiGraphicsExtractor g, Slot s, float prog) {
        int n = Ability.ICON_TEX_SIZE;
        float limit = (float) (prog * Math.PI * 2);
        float c = n / 2F;
        g.pose().pushMatrix();
        g.pose().translate(s.x(), s.y());
        g.pose().scale(ICON / (float) n, ICON / (float) n);
        for (int ty = 0; ty < n; ty++) {
            int runStart = -1;
            for (int tx = 0; tx <= n; tx++) {
                boolean on = tx < n && angle(tx + 0.5F - c, ty + 0.5F - c) < limit;
                if (on && runStart < 0) runStart = tx;
                else if (!on && runStart >= 0) {
                    g.blit(RenderPipelines.GUI_TEXTURED, s.ability().icon(), runStart, ty, runStart, ty, tx - runStart, 1, n, n, 0xFFFFFFFF);
                    runStart = -1;
                }
            }
        }
        // the hand itself: a thin bright line from the centre toward the edge
        double ang = limit;
        for (int r = 0; r < n / 2; r++) {
            int hx = (int) Math.floor(c + Math.sin(ang) * r), hy = (int) Math.floor(c - Math.cos(ang) * r);
            g.fill(hx, hy, hx + 1, hy + 1, 0xC0F0E0FF);
        }
        g.pose().popMatrix();
    }

    /** clockwise angle from 12 o'clock, 0..2pi (screen y grows downward) */
    static float angle(float dx, float dy) {
        double a = Math.atan2(dx, -dy);
        return (float) (a < 0 ? a + Math.PI * 2 : a);
    }

    static String shortKey(String s) {
        return s.replace("Left ", "L").replace("Right ", "R").replace("Control", "Ctrl").replace("Button ", "M");
    }

    private AbilityHud() {}
}
