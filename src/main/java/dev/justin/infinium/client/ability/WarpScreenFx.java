package dev.justin.infinium.client.ability;

import dev.justin.infinium.SpacetimeWarp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Quantum Tunnel (batch1 B5) client feedback: a brief, subtle tier-tinted edge vignette + faint flash drawn on the
 * HUD for {@link #DURATION_MS} after the server confirms the tunnel fired. Also mirrors the forward launch on the
 * client so it can't be eaten by the teleport's position sync. Only the warping player sees it.
 */
public final class WarpScreenFx {
    /** Overlay length and strength (tunables). */
    static final long DURATION_MS = 380;
    static final int MAX_EDGE_ALPHA = 0x70;   // vignette peak alpha at the screen edge
    static final int MAX_FLASH_ALPHA = 0x22;  // whole-screen flash peak
    static final int[] TINT = {0x8A5CFF, 0xB040FF, 0xFF6ACD};   // Astral / Singularity / Infinity

    private static long startNanos = 0;
    private static int tint = TINT[0];

    /** Called on the client thread when the server reports the warp fired (AbilityClient.onFired). */
    public static void onFired(Player pl) {
        int t = Math.max(1, SpacetimeWarp.tier(pl));
        tint = TINT[t - 1];
        startNanos = System.nanoTime();
        pl.setSprinting(true);
        Vec3 look = pl.getLookAngle();
        if (look.lengthSqr() > 1e-6) {
            pl.setDeltaMovement(look.normalize().scale(SpacetimeWarp.LAUNCH_SPEED[t - 1]).add(0, SpacetimeWarp.LAUNCH_LIFT, 0));
        }
    }

    public static void render(GuiGraphicsExtractor g) {
        if (startNanos == 0) return;
        float f = (System.nanoTime() - startNanos) / 1_000_000F / DURATION_MS;
        if (f >= 1F) { startNanos = 0; return; }
        float k = (float) Math.sin(Math.PI * Math.min(1F, f * 1.6F)) * (1F - f);   // fast rise, soft fall
        int w = g.guiWidth(), h = g.guiHeight();
        int flash = (int) (MAX_FLASH_ALPHA * k * (1F - f));
        if (flash > 0) g.fill(0, 0, w, h, (flash << 24) | tint);
        int rings = 10, band = Math.max(2, Math.min(w, h) / 40);
        for (int i = 0; i < rings; i++) {
            int a = (int) (MAX_EDGE_ALPHA * k * (1F - i / (float) rings) * (1F - i / (float) rings));
            if (a <= 0) continue;
            int c = (a << 24) | tint, o = i * band;
            g.fill(o, o, w - o, o + band, c);                          // top
            g.fill(o, h - o - band, w - o, h - o, c);                  // bottom
            g.fill(o, o + band, o + band, h - o - band, c);            // left
            g.fill(w - o - band, o + band, w - o, h - o - band, c);    // right
        }
    }

    private WarpScreenFx() {}
}
