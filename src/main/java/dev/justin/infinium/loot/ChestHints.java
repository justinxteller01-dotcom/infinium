package dev.justin.infinium.loot;

import dev.justin.infinium.Infinium;
import dev.justin.infinium.StyleLoot;
import dev.justin.infinium.dungeon.DungeonReset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Server-only. Each player gets hints for chests whose Infinium share they have not taken.
 * Loaded chunks only. Never force-loads. Sent when the set changes.
 * Not gated by the World Effects toggles.
 */
public final class ChestHints {
    private static final Map<UUID, String> LAST = new HashMap<>();
    private static int tick, chunkScanAt = -1;
    /** R6g: chunk LOAD only queues; the server tick processes. Never touch Level/chunk lookups or setChanged inside LOAD (C2ME deadlock). */
    private record Pending(ServerLevel level, int cx, int cz, long[] positions) {}
    private static final java.util.concurrent.ConcurrentLinkedQueue<Pending> QUEUE = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public static void register() {
        NaturalMark.init();
        PayloadTypeRegistry.clientboundPlay().register(ChestHintPayload.TYPE, ChestHintPayload.CODEC);
        ServerChunkEvents.CHUNK_LOAD.register(ChestHints::onChunk);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ++tick;
            drainQueue();
            boolean chunkScan = chunkScanAt >= 0 && tick >= chunkScanAt;
            if ((tick % 100) != 0 && !chunkScan) return;   // ~5 s view scan, or ~1 s after a chunk load
            chunkScanAt = -1;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) refresh(player);
            NaturalRefill.saveTick();
        });
    }

    /** Remembers natural chest tables as their chunk loads (server thread). Lootr containers keep the table in their own data. */
    private static void onChunk(ServerLevel level, LevelChunk chunk, boolean newlyGenerated) {
        long[] ps;
        try {
            var keys = new ArrayList<>(chunk.getBlockEntitiesPos());   // positions only, from the chunk passed in
            if (keys.isEmpty()) return;
            ps = new long[keys.size()];
            for (int i = 0; i < ps.length; i++) ps[i] = keys.get(i).asLong();
        } catch (Exception e) { return; }
        QUEUE.add(new Pending(level, chunk.getPos().x(), chunk.getPos().z(), ps));
    }

    /** Server thread, END_SERVER_TICK. Only chunks that are loaded now (getChunkNow never loads); others are dropped
     *  (they are queued again by their next LOAD). Bounded per tick. */
    private static void drainQueue() {
        int budget = 64;
        Pending p;
        while (budget-- > 0 && (p = QUEUE.poll()) != null) {
            LevelChunk chunk = p.level().getChunkSource().getChunkNow(p.cx(), p.cz());
            if (chunk == null) continue;
            boolean dirtyChunk = false;
            var bes = chunk.getBlockEntities();
            for (long l : p.positions()) {
                BlockEntity be = bes.get(BlockPos.of(l));
                if (!(be instanceof RandomizableContainerBlockEntity box)) continue;
                Identifier id = dev.justin.infinium.RoguelikeCompat.containerTable(box);
                if (id == null) continue;
                if (NaturalMark.mark(be)) dirtyChunk = true;                // R6d: still has a loot table -> naturally generated
                NaturalRefill.rememberTable(p.level(), be.getBlockPos(), id);
                if ("roguelike".equals(id.getNamespace())) NaturalRefill.rememberAnyTable(p.level(), be.getBlockPos(), id);
            }
            if (dirtyChunk) chunk.markUnsaved();
            if (chunkScanAt < 0 && !p.level().players().isEmpty()) chunkScanAt = tick + 20;
        }
    }

    private static void refresh(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        int dist = Math.min(8, player.level().getServer().getPlayerList().getViewDistance());
        int r = dist * 16;
        BlockPos at = player.blockPosition();
        List<ChestHintPayload.Hint> hints = new ArrayList<>();
        int cx0 = (at.getX() - r) >> 4, cx1 = (at.getX() + r) >> 4;
        int cz0 = (at.getZ() - r) >> 4, cz1 = (at.getZ() + r) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (hints.size() >= 64) break;
                int code = codeFor(level, player, be);
                if (code == 0) continue;
                BlockPos p = be.getBlockPos();
                if (p.distSqr(at) > (double) r * r) continue;
                hints.add(new ChestHintPayload.Hint(p.getX(), p.getY(), p.getZ(), code));
            }
        }
        for (var site : dev.justin.infinium.RoguelikeCompat.sites(level)) {
            if (!site.isIndexed()) continue;
            for (long packed : site.chests()) {
                if (hints.size() >= 64) break;
                BlockPos p = BlockPos.of(packed);
                if (p.distSqr(at) > (double) r * r) continue;
                if (level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null) continue;
                if (!(level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity dbe) || !NaturalMark.natural(dbe)) continue;   // gone / player-placed
                if (DungeonReset.claimed(level, site, player.getUUID(), p)) continue;
                int code = ShareRoll.hintCode(dev.justin.infinium.DungeonLoot.preview(level, p, player.getUUID()));
                if (code != 0) hints.add(new ChestHintPayload.Hint(p.getX(), p.getY(), p.getZ(), code));
            }
        }
        String sig = sig(hints);
        if (sig.equals(LAST.get(player.getUUID()))) return;
        LAST.put(player.getUUID(), sig);
        ServerPlayNetworking.send(player, new ChestHintPayload(hints));
    }

    private static int codeFor(ServerLevel level, ServerPlayer player, BlockEntity be) {
        BlockPos pos = be.getBlockPos();
        if (!(be instanceof RandomizableContainerBlockEntity box)) return 0;
        if (!NaturalMark.natural(be)) return 0;                  // R6d: player-placed container at a tracked position
        Identifier table = NaturalRefill.rememberedTable(level, pos);
        if (table == null) return 0;
        StyleLoot.Tier tier = StyleLoot.tierFor(table);
        if (tier == null) return 0;
        // R6c: due -> reset now (clear + arm full re-roll) so the hint reflects the re-rolled share before opening
        if (NaturalRefill.due(level, player.getUUID(), pos)) NaturalRefill.resetIfDue(player, level, pos, be);
        boolean lootrBe = LootrBridge.isLootr(be);
        boolean unrolled = lootrBe ? LootrBridge.playerInventory(be, player.getUUID()) == null : box.getLootTable() != null;
        if (NaturalRefill.pending(level, player.getUUID(), pos)) {
            if (!unrolled) return 0;                     // vanilla re-roll already taken by someone else
            int cyc = NaturalRefill.seedCycle(level, player.getUUID(), pos);
            return ShareRoll.hintCode(ShareRoll.natural(ShareSeed.natural(level.getSeed(), pos, player.getUUID(), cyc), tier));
        }
        boolean due = NaturalRefill.due(level, player.getUUID(), pos);
        if (due && !unrolled) return 0;                  // reset could not run (should not happen)
        boolean show = !NaturalRefill.opened(level, player.getUUID(), pos) || due;
        if (!show) return 0;
        // Vanilla (shared) chest already rolled by someone else: this player's cycle-0 share is gone with it.
        boolean lootr = be.getClass().getName().startsWith("noobanidus.mods.lootr");
        if (!lootr && !due && box.getLootTable() == null) return 0;
        int cycle = NaturalRefill.seedCycle(level, player.getUUID(), pos);
        long seed = ShareSeed.natural(level.getSeed(), pos, player.getUUID(), cycle);
        return ShareRoll.hintCode(ShareRoll.natural(seed, tier));
    }

    private static String sig(List<ChestHintPayload.Hint> hints) {
        StringBuilder sb = new StringBuilder();
        for (var h : hints) sb.append(h.x()).append(',').append(h.y()).append(',').append(h.z()).append(':').append(h.kind()).append(';');
        return sb.toString();
    }

    public static void markDirty(UUID player) { LAST.remove(player); }

    private ChestHints() {}
}
