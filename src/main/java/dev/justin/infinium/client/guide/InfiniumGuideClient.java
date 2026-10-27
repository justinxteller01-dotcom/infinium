package dev.justin.infinium.client.guide;

import dev.justin.infinium.InfiniumBook;
import dev.justin.infinium.InfiniumGuide;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;

/** Client half of the guide, plus removal of the now-redundant Style / Set tooltip lines. */
public final class InfiniumGuideClient {
    private InfiniumGuideClient() {}

    public static void init() {
        InfiniumGuide.OPENER = InfiniumGuideClient::open;
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> lines.removeIf(InfiniumGuideClient::redundant));
        // UNVERIFIED: ClientTickEvents.END_CLIENT_TICK and ParticleTypes.WITCH on this jar.
        ClientTickEvents.END_CLIENT_TICK.register(InfiniumGuideClient::heldParticles);
    }

    public static void open(Player player) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new InfiniumGuideScreen(mc.gui.screen()));
    }

    /** A few purple particles while the guide is in the main hand. */
    private static void heldParticles(Minecraft client) {
        if (client.player == null || client.level == null) return;
        if (InfiniumBook.GUIDE == null || !client.player.getMainHandItem().is(InfiniumBook.GUIDE)) return;
        if ((client.player.tickCount & 3) != 0) return;
        client.level.addParticle(ParticleTypes.WITCH,
                client.player.getX(), client.player.getY() + 1.0, client.player.getZ(), 0, 0.02, 0);
    }

    /** The item name already says the set. "Set: Infinity" is the same fact. */
    private static boolean redundant(Component line) {
        if (line.getContents() instanceof TranslatableContents tc) {
            String key = tc.getKey();
            if ("infinium.style.line".equals(key) || "infinium.set.infinity".equals(key)) return true;
        }
        String text = line.getString();
        return text.startsWith("Style:") || "Set: Infinity".equals(text);
    }
}
