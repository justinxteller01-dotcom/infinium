package dev.justin.infinium.client;

import dev.justin.infinium.loot.ChestHintPayload;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;

/** Renders this player's chest hints. Always on. Not tied to World Effects toggles. */
public final class ChestHintClient {
    private static List<ChestHintPayload.Hint> hints = List.of();
    private static final DustParticleOptions FRAG = new DustParticleOptions(0xB04CFF, 0.45F);
    private static final DustParticleOptions ASTRAL = new DustParticleOptions(0xC45AD4, 0.7F);
    private static final DustParticleOptions CYBER = new DustParticleOptions(0xD24A8A, 0.7F);
    private static final DustParticleOptions SING = new DustParticleOptions(0xA14AD0, 1.0F);
    private static final DustParticleOptions PSYCHO = new DustParticleOptions(0xE070C0, 1.05F);

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ChestHintPayload.TYPE, (payload, ctx) -> hints = payload.hints());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> hints = List.of());
        ClientTickEvents.END_LEVEL_TICK.register(level -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.isPaused() || mc.level == null || hints.isEmpty()) return;
            long t = mc.level.getGameTime();
            if ((t % 6) != 0) return;
            for (ChestHintPayload.Hint h : hints) {
                DustParticleOptions dust = switch (h.kind()) {
                    case 2 -> ASTRAL;
                    case 3 -> CYBER;
                    case 4 -> SING;
                    case 5 -> PSYCHO;
                    default -> FRAG;
                };
                int n = h.kind() >= 4 ? 3 : h.kind() >= 2 ? 2 : 1;
                for (int i = 0; i < n; i++)
                    mc.level.addParticle(dust, h.x() + 0.5, h.y() + 0.9 + i * 0.15, h.z() + 0.5, 0, 0.01, 0);
            }
        });
    }

    private ChestHintClient() {}
}
