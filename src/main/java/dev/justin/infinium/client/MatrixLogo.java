package dev.justin.infinium.client;

import dev.justin.infinium.Infinium;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * M5 matrix perk logos: textures/gui/matrix/<kind>.png, a vertical strip of FRAMES square frames (placeholder art from PIL,
 * see README_ART.txt). LIT = frame animation + a gentle scale pulse + a glow halo in the set accent;
 * OFF ("lights off") = frame 0, dimmed, no motion; LOCKED = dark silhouette with a padlock.
 */
public final class MatrixLogo {
    public static final int LIT = 0, OFF = 1, LOCKED = 2;
    public static final int FRAMES = 8;

    static Identifier tex(String kind) { return Infinium.id("textures/gui/matrix/" + kind + ".png"); }

    public static void draw(GuiGraphicsExtractor g, String kind, int x, int y, int size, int state, int accent, int phase) {
        long ms = System.currentTimeMillis() + phase * 137L;
        boolean anim = state == LIT && InfiniumClientConfig.uiEffects();
        int frame = anim ? (int) ((ms / 110) % FRAMES) : 0;
        int tint = switch (state) {
            case LIT -> 0xFFFFFFFF;
            case OFF -> 0x99707078;
            default -> 0x88302838;
        };
        if (state == LIT) {   // glow halo behind the logo
            double w = 0.5 + 0.5 * Math.sin(ms / 260.0);
            int rgb = accent & 0xFFFFFF;
            for (int k = 3; k >= 1; k--) {
                int a = (int) ((anim ? 18 + 22 * w : 26) / k);
                int o = k * 2;
                g.fill(x - o, y - o, x + size + o, y + size + o, (a << 24) | rgb);
            }
        }
        var pose = g.pose();
        pose.pushMatrix();
        float sc = anim ? 1F + 0.06F * (float) Math.sin(ms / 300.0) : 1F;
        pose.translate(x + size / 2F, y + size / 2F);
        pose.scale(sc, sc);
        pose.translate(-size / 2F, -size / 2F);
        g.blit(RenderPipelines.GUI_TEXTURED, tex(kind), 0, 0, 0F, (float) (frame * size), size, size, size, size * FRAMES, tint);
        pose.popMatrix();
        if (state == LOCKED) {   // padlock, bottom-right
            int lx = x + size - 8, ly = y + size - 8;
            g.fill(lx + 1, ly - 3, lx + 6, ly - 2, 0xFFB8A8C8);
            g.fill(lx + 1, ly - 3, lx + 2, ly, 0xFFB8A8C8);
            g.fill(lx + 5, ly - 3, lx + 6, ly, 0xFFB8A8C8);
            g.fill(lx, ly, lx + 7, ly + 6, 0xFFB8A8C8);
            g.fill(lx + 3, ly + 2, lx + 4, ly + 4, 0xFF201828);
        }
    }

    private MatrixLogo() {}
}
