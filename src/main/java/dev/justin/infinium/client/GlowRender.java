package dev.justin.infinium.client;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Forced to entityTranslucent for better compatibility with Sodium / equipment layers. */
public final class GlowRender {
    public static RenderType type(Identifier tex) {
        return RenderTypes.entityTranslucent(tex);
    }
    private GlowRender() {}
}
