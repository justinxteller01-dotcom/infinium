package dev.justin.infinium.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.justin.infinium.DungeonFinder;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.world.phys.Vec3;

/** Beacon beams on Roguelike dungeon entrance towers while a Dungeon Finder is held (main or off hand).
 *  Beams get wider with distance so they stay visible out to the far plane (with Distant Horizons the far plane is the
 *  LOD distance; beyond the camera far plane nothing can be drawn). */
public final class DungeonBeams {
    private static volatile List<DungeonFinder.Beam> beams = List.of();
    private static volatile long lastMs;
    static final int[] COLORS = {0xFF3CFF3C, 0xFFFFE23C, 0xFFFF3030};   // green normal, yellow large, red extra large

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DungeonFinder.BeamsPayload.TYPE, (pl, ctx) -> { beams = pl.beams(); lastMs = System.currentTimeMillis(); });
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            if (!mc.player.getMainHandItem().is(DungeonFinder.ITEM) && !mc.player.getOffhandItem().is(DungeonFinder.ITEM)) return;
            if (System.currentTimeMillis() - lastMs > 5000) return;
            Vec3 cam = ctx.levelState().cameraRenderState.pos;
            PoseStack ps = ctx.poseStack();
            float anim = (float) Math.floorMod(mc.level.getGameTime(), 40L);
            for (DungeonFinder.Beam b : beams) {
                double dx = b.x() + 0.5 - cam.x, dz = b.z() + 0.5 - cam.z;
                float scale = (float) Math.max(1.0, Math.sqrt(dx * dx + dz * dz) / 96.0);
                int base = b.y(), height = Math.max(16, 2048 - base);
                ps.pushPose();
                ps.translate(b.x() - cam.x, base - cam.y, b.z() - cam.z);
                BeaconRenderer.submitBeaconBeam(ps, ctx.submitNodeCollector(), BeaconRenderer.BEAM_LOCATION, 1.0F, anim, 0, height,
                    COLORS[Math.max(0, Math.min(2, b.size()))], 0.2F * scale, 0.25F * scale);
                ps.popPose();
            }
        });
    }
    private DungeonBeams() {}
}
