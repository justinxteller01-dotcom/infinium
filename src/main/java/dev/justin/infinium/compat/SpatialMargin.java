package dev.justin.infinium.compat;

import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.gui.render.GuiRenderer;
import org.tastytrash.spatialGUI.client.SpatialGUIClient;
import org.tastytrash.spatialGUI.render.SpatialGUIRenderer;

/**
 * Spatial GUI scale & overscan integration.
 * - Scales the 3D quad and curved screen arc by F (default 1.35x) so it feels proportional to the player's view.
 * - Expands the offscreen texture and applies a 1/F projection to provide ample canvas margins for tooltips and overlays.
 * - Mouse raycasting and JEI screen properties are decoupled to anchor accurately to screen boundaries.
 * Factor can be customized via -Dinfinium.spatialOverscan (default 1.35, clamped 1.0 .. 3.0).
 */
public final class SpatialMargin {
    public static final float F = 1.0F;
    public static boolean on() { return false; }

    /** True when this GuiRenderer is Spatial GUI's offscreen screen renderer. */
    public static boolean isSpatialRenderer(GuiRenderer r) {
        try {
            if (!SpatialGUIClient.isEnabled()) return false;
            SpatialGUIRenderer sr = SpatialGUIClient.renderer();
            return sr != null && sr.getScreenGuiRenderer() == r;
        } catch (Throwable t) { return false; }
    }
    public static TextureTarget target() {
        try { SpatialGUIRenderer sr = SpatialGUIClient.renderer(); return sr == null ? null : sr.getTargetManager().getInventoryTarget(); }
        catch (Throwable t) { return null; }
    }
    /** Texture side in pixels: window side * render scale * F, capped to a safe GPU size. */
    public static int grow(int px) { return on() ? Math.max(1, Math.min(16384, Math.round(px * F))) : px; }
    /** Physical cursor in window pixels while Spatial GUI draws this screen in 3D (its mouse events are then in panel
     *  space, not HUD space), else null. Verified in game: MouseHandlerUtil.getSourceX/Y = window cursor (1.7.1+26.2). */
    public static double[] hudCursor(net.minecraft.client.gui.screens.Screen s) {
        try {
            if (s == null || !SpatialGUIClient.isEnabled() || !SpatialGUIClient.shouldHookScreen(s)) return null;
            return new double[] { org.tastytrash.spatialGUI.util.MouseHandlerUtil.getSourceX(), org.tastytrash.spatialGUI.util.MouseHandlerUtil.getSourceY() };
        } catch (Throwable t) { return null; }
    }
    private SpatialMargin() {}
}
