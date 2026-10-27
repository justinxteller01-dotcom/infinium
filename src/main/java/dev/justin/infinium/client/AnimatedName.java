package dev.justin.infinium.client;

import dev.justin.infinium.InfiniumStyle;
import dev.justin.infinium.StyleTemplates;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Tooltip title for Singularity / CyberPsycho items and templates: 1.25x text, redrawn every frame.
 *  - Singularity: fast purple shimmer running across the letters (violet / magenta / indigo, white sparks).
 *  - CyberPsycho: fast-updating magenta/violet with a slight glitch (1 px jitter, brief chromatic ghost) that stays readable.
 */
public final class AnimatedName implements ClientTooltipComponent {
    public static final float SCALE = 1.25F;
    /** Item of the tooltip being prepared (setTooltipForNextFrame(Font, ItemStack, ...)); cleared once consumed. */
    public static ItemStack pending = ItemStack.EMPTY;

    private final String text; private final InfiniumStyle style; private final boolean infinity;
    private AnimatedName(String text, InfiniumStyle style, boolean infinity) { this.text = text; this.style = style; this.infinity = infinity; }
    /** Infinity armor, or an Infinium tool while the local player wears the full Infinity set. */
    static boolean infinity(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        if (dev.justin.infinium.Infinium.isInfinity(s)) return true;
        return isTool(s) && HolderStyleProperty.of(net.minecraft.client.Minecraft.getInstance().player) == dev.justin.infinium.SetLook.INFINITY;
    }

    public static InfiniumStyle animatedStyle(ItemStack s) {
        if (s == null || s.isEmpty()) return null;
        InfiniumStyle st = StyleTemplates.styleOf(s.getItem());
        if (st == null) st = InfiniumStyle.get(s);
        if (st == null && isTool(s)) {   // tools follow the wearer's full-set look
            dev.justin.infinium.SetLook l = HolderStyleProperty.of(net.minecraft.client.Minecraft.getInstance().player);
            st = l.ordinal() < 4 ? InfiniumStyle.values()[l.ordinal()] : null;
        }
        return st != null && st.rarer ? st : null;
    }
    static boolean isTool(ItemStack s) {
        return s.is(dev.justin.infinium.Infinium.SWORD) || s.is(dev.justin.infinium.Infinium.PICKAXE) || s.is(dev.justin.infinium.Infinium.AXE)
            || s.is(dev.justin.infinium.Infinium.SHOVEL) || s.is(dev.justin.infinium.Infinium.HOE);
    }
    static String plain(FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((i, st, cp) -> { sb.appendCodePoint(cp); return true; });
        return sb.toString();
    }
    /** Replace the title line of an item tooltip when the item is a Singularity / CyberPsycho piece or template. */
    public static List<ClientTooltipComponent> wrap(List<ClientTooltipComponent> list) {
        ItemStack s = pending;
        if (s == null || s.isEmpty() || list == null || list.isEmpty() || !(list.get(0) instanceof ClientTextTooltip t0)) return list;
        InfiniumStyle st = animatedStyle(s);
        boolean inf = infinity(s);
        if (st == null && !inf) return list;
        String title = plain(((ClientTextTooltipAccessor) t0).infinium$text());
        if (!title.equals(s.getHoverName().getString())) return list;    // stale / unrelated tooltip
        pending = ItemStack.EMPTY;
        List<ClientTooltipComponent> out = new ArrayList<>(list);
        out.set(0, new AnimatedName(title, st, inf));
        return out;
    }

    @Override public int getWidth(Font font) { return (int) Math.ceil(font.width(text) * SCALE) + 2; }
    @Override public int getHeight(Font font) { return 13; }

    @Override
    public void extractText(GuiGraphicsExtractor g, Font font, int x, int y) {
        long ms = System.currentTimeMillis();
        g.pose().pushMatrix();
        g.pose().translate(x, y - 1);
        g.pose().scale(SCALE, SCALE);
        if (infinity) { if ((hash(ms / 400, 11) & 3) == 0) cyberpsycho(g, font, ms); else singularity(g, font, ms); }   // Infinity: Singularity shimmer, Cyber Psycho glitch bursts
        else if (style == InfiniumStyle.SINGULARITY) singularity(g, font, ms); else cyberpsycho(g, font, ms);
        g.pose().popMatrix();
    }

    private void singularity(GuiGraphicsExtractor g, Font font, long ms) {
        double t = ms / 1000.0; int cx = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i); String ch = new String(Character.toChars(cp)); i += Character.charCount(cp);
            double w = Math.sin(t * 9.0 - cx * 0.45) * 0.5 + 0.5;                 // fast wave across the word
            double w2 = Math.sin(t * 23.0 + cx * 1.7) * 0.5 + 0.5;                // fast flicker, per letter
            float hue = (float) (0.74 + 0.12 * w - 0.04 * w2);                    // indigo .. violet .. magenta
            float sat = (float) (0.85 - 0.6 * Math.pow(w * w2, 3));               // occasional near-white spark
            float val = (float) (0.75 + 0.25 * w);
            g.text(font, ch, cx, 0, 0xFF000000 | net.minecraft.util.Mth.hsvToRgb(hue, sat, val) & 0xFFFFFF, true);
            cx += font.width(ch);
        }
    }

    private static int hash(long a, int b) { long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL; h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; return (int) (h ^ (h >>> 32)); }

    private void cyberpsycho(GuiGraphicsExtractor g, Font font, long ms) {
        long tick = ms / 70;                                       // ~14 updates per second
        boolean burst = (hash(ms / 400, 7) & 7) == 0;              // short glitch bursts, ~1 in 8 windows
        int cx = 0, idx = 0;
        if (burst) {                                               // faint chromatic ghost behind the text
            int ox = (hash(tick, 1) & 1) == 0 ? -1 : 1;
            g.text(font, text, ox, 0, 0x70B020FF, false);
            g.text(font, text, -ox, 0, 0x70FF2080, false);
        }
        for (int i = 0; i < text.length(); idx++) {
            int cp = text.codePointAt(i); String ch = new String(Character.toChars(cp)); i += Character.charCount(cp);
            int h = hash(tick, idx);
            int dy = burst && (h & 15) == 0 ? ((h >> 4) & 1) * 2 - 1 : 0;      // 1 px jitter on a few letters
            double pulse = Math.sin(ms / 1000.0 * 14.0 - idx * 0.8) * 0.5 + 0.5;
            int col;
            if (((h >> 8) & 31) == 0) col = 0xFFFF9CF0;                          // single-letter bright pink flash
            else if (burst && ((h >> 13) & 7) == 0) col = 0xFFE040FF;            // magenta glitch letter (no cyan)
            else col = 0xFF000000 | net.minecraft.util.Mth.hsvToRgb((float) (0.80 + 0.07 * pulse), (float) (0.75 - 0.3 * pulse), 1.0F) & 0xFFFFFF;
            g.text(font, ch, cx, dy, col, true);
            cx += font.width(ch);
        }
    }
}
