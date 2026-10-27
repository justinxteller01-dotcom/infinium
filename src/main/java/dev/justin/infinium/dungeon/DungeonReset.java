package dev.justin.infinium.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.RoguelikeCompat;
import dev.justin.infinium.loot.ChestHints;
import dev.justin.infinium.loot.LootTuning;
import dev.justin.infinium.loot.LootrBridge;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

/**
 * R6 (rewritten by the builder): Roguelike Dungeon reset. Server thread only. Never loads a chunk.
 * Dungeons and their room boxes come from RoguelikeCompat's index (the same one DungeonLoot uses), so worldgen and
 * command/structure-placed dungeons are handled the same way.
 *  - Snapshot: once a dungeon is indexed, each room box is captured (dense block states incl. air + block-entity NBT) when all
 *    of its chunks are loaded, a few boxes per tick. Gzip NBT at <world>/infinium_snapshots/<dim>_<x>_<z>.nbt.gz.
 *  - Reset: after a player has been inside, once nobody has been inside for emptyMinutes of server-up wall time, the boxes are
 *    rebuilt from the snapshot (only changed blocks, bounded per tick, loaded chunks only; unloaded boxes finish later). Paused
 *    while anyone is inside. Containers that still exist keep their contents; a rebuilt (broken) vanilla container comes back
 *    without its loot table so breaking chests cannot farm loot.
 *  - Loot: each player's Infinium share per chest is paid once per cycle (claim()). 72 real hours after that player's first
 *    chest in the cycle, applied at the next reset: the cycle advances (new DungeonLoot plan), the player's Lootr inventories
 *    for these chests are cleared (Lootr public API, reflection), and vanilla chests get their loot table back.
 * R6c: loot timer is lootRefreshDays (7) real days per player per dungeon, checked lazily; when due the player's chests are
 *  wiped immediately (Lootr per-player inventory cleared; vanilla: contents cleared + loot table restored) for a full re-roll.
 * State: <world>/infinium_dungeon_state.json.
 */
public final class DungeonReset {
    private static final Map<String, D> DUNGEONS = new LinkedHashMap<>();
    private static final Map<UUID, String> INSIDE = new HashMap<>();
    private static final Map<UUID, String> LAST_HUD = new HashMap<>();
    private static MinecraftServer server;
    private static boolean dirty;
    private static long lastMs;
    private static final int CHANGES_PER_TICK = 384, COMPARES_PER_TICK = 12000, CAPTURE_BLOCKS_PER_TICK = 16000;

    public static void register() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(DungeonHudPayload.TYPE, DungeonHudPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> { server = s; lastMs = System.currentTimeMillis(); load(); });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { flushSnapshots(); save(); DUNGEONS.clear(); INSIDE.clear(); LAST_HUD.clear(); server = null; });
        ServerTickEvents.END_SERVER_TICK.register(DungeonReset::tick);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, srv) -> { INSIDE.remove(h.player.getUUID()); LAST_HUD.remove(h.player.getUUID()); });
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (server != null && !world.isClientSide() && hand == net.minecraft.world.InteractionHand.MAIN_HAND
                    && player instanceof ServerPlayer sp && world instanceof ServerLevel level && !player.isSpectator()
                    && level.getBlockEntity(hit.getBlockPos()) instanceof RandomizableContainerBlockEntity) onOpen(level, sp, hit.getBlockPos());
            return net.minecraft.world.InteractionResult.PASS;
        });
    }

    // ---------------------------------------------------------------- loot hooks (DungeonLoot / ChestHints)

    /** Current reset cycle of this player in this dungeon (0 = R5c plan). */
    public static int cycle(ServerLevel level, RoguelikeCompat.Site site, UUID who) {
        D d = site == null ? null : DUNGEONS.get(key(level, site));
        P p = d == null ? null : live(level, d, who, true);
        return p == null ? 0 : p.cycle;
    }

    /** True when this player's share at pos is already taken this cycle. */
    public static boolean claimed(ServerLevel level, RoguelikeCompat.Site site, UUID who, BlockPos pos) {
        D d = site == null ? null : DUNGEONS.get(key(level, site));
        P p = d == null ? null : live(level, d, who, true);
        return p != null && p.claimed.contains(pos.asLong());
    }

    /** Called when a roguelike chest table is rolled for a player. Returns false if the share was already paid this cycle. */
    public static boolean claim(ServerLevel level, RoguelikeCompat.Site site, ServerPlayer player, BlockPos pos) {
        if (site == null) return true;                         // not an indexed dungeon: R5c fallback roll, untracked
        D d = dungeon(level, site);
        live(level, d, player.getUUID(), false);   // mid-roll: never wipe the chest being filled
        P p = d.players.computeIfAbsent(player.getUUID(), u -> new P());
        if (!p.claimed.add(pos.asLong())) return false;
        if (p.firstOpenMs == 0) p.firstOpenMs = System.currentTimeMillis();
        dirty = true;
        ChestHints.markDirty(player.getUUID());
        return true;
    }

    /** R6b lazy 72h rule: timestamp check on access (hints scan, roll, open); no reliance on the dungeon reset tick. */
    private static P live(ServerLevel level, D d, UUID who, boolean wipe) {
        P p = d.players.get(who);
        if (p != null && due(p, System.currentTimeMillis())) {
            List<Long> was = new ArrayList<>(p.claimed);
            refreshPlayer(d, who, p);
            if (wipe) for (long c : was) wipeNow(level, d, who, BlockPos.of(c));
        }
        return p;
    }

    /** R6c: immediate wipe + full re-roll arm for one chest (loaded chunks only; others via lootrClear / rebuild refillVanilla). */
    private static void wipeNow(ServerLevel level, D d, UUID who, BlockPos pos) {
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!dev.justin.infinium.loot.NaturalMark.natural(be)) return;   // R6d: never a player-placed container
        if (LootrBridge.isLootr(be)) {
            if (d.lootrClear.remove(who + "|" + pos.asLong())) LootrBridge.clearPlayerChest(level, who, pos);
        } else if (be instanceof RandomizableContainerBlockEntity box && box.getLootTable() == null) {
            var table = dev.justin.infinium.loot.NaturalRefill.rememberedAnyTable(level, pos);
            if (table == null) return;                   // unknown table: rebuild path restores it from the snapshot
            for (int i = 0; i < box.getContainerSize(); i++) box.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
            box.setLootTable(net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE, table), level.getRandom().nextLong());
            box.setChanged();
        }
        dirty = true;
    }

    /** Open fallback: refresh if due and clear this player's stale Lootr inventory for this chest right now (chunk is loaded). */
    private static void onOpen(ServerLevel level, ServerPlayer player, BlockPos pos) {
        RoguelikeCompat.Site site = RoguelikeCompat.findSite(level, pos);
        if (site == null) return;
        D d = DUNGEONS.get(key(level, site));
        if (d == null) return;
        live(level, d, player.getUUID(), true);
        if (d.lootrClear.remove(player.getUUID() + "|" + pos.asLong())) { LootrBridge.clearPlayerChest(level, player.getUUID(), pos); dirty = true; }
    }

    // ---------------------------------------------------------------- commands

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        for (D d : DUNGEONS.values()) out.add(info(d));
        return out;
    }

    public static String infoNearest(ServerLevel level, BlockPos at) { D d = nearest(level, at); return d == null ? "No dungeon recorded in this dimension." : info(d); }

    /** Force: loot refresh for every player now, then rebuild (still waits while someone is inside). */
    public static String forceReset(ServerLevel level, BlockPos at, String id) {
        D d = id == null || id.equals("nearest") ? nearest(level, at) : DUNGEONS.get(id);
        if (d == null) for (D x : DUNGEONS.values()) if (x.key.endsWith("|" + id)) d = x;
        if (d == null) return "No such dungeon. Use /infinium dungeon list.";
        if (!d.snapshotDone()) return "Dungeon " + d.key + " has no complete snapshot yet (" + d.captured.size() + "/" + d.boxCount + " rooms).";
        for (var e : d.players.entrySet()) refreshPlayer(d, e.getKey(), e.getValue());
        d.visited = true;
        startRestore(d);
        return "Reset queued for " + d.key + (d.occupied ? " (waits until nobody is inside)" : "") + ".";
    }

    private static String info(D d) {
        long need = LootTuning.emptyMinutes * 60_000L;
        return d.key + ": rooms " + d.boxCount + ", snapshot " + (d.snapshotDone() ? "complete" : d.captured.size() + "/" + d.boxCount)
                + ", " + (d.occupied ? "occupied" : "empty " + d.emptyMs / 60_000L + "/" + need / 60_000L + "m")
                + (d.restoring ? ", rebuilding (" + d.pending.size() + " rooms left)" : d.visited ? ", reset pending" : "")
                + ", players " + d.players.size();
    }

    // ---------------------------------------------------------------- tick

    private static void tick(MinecraftServer srv) {
        if (server == null) return;
        long now = System.currentTimeMillis();
        long dt = Math.max(0, Math.min(5_000L, now - lastMs));   // server-up wall time only; long stalls and downtime do not count
        lastMs = now;
        int t = srv.getTickCount();
        for (ServerLevel level : srv.getAllLevels()) {
            List<RoguelikeCompat.Site> sites = RoguelikeCompat.sites(level);
            if (sites.isEmpty()) continue;
            if (t % 20 == 0) for (RoguelikeCompat.Site s : sites) if (s.isIndexed() && !s.roomBoxes().isEmpty()) dungeon(level, s);
            String dim = dim(level);
            for (D d : DUNGEONS.values()) {
                if (!d.dim.equals(dim)) continue;
                boolean occupied = false;
                for (ServerPlayer p : level.players()) if (!p.isSpectator() && d.contains(p.blockPosition())) { occupied = true; enter(p, d); }
                d.occupied = occupied;
                if (!d.snapshotDone()) capture(level, d);               // as early as possible, even while occupied
                if (occupied) { d.visited = true; d.emptyMs = 0; continue; }
                d.emptyMs += dt;
                if (!d.snapshotDone()) continue;
                if (!d.restoring && d.emptyMs >= LootTuning.emptyMinutes * 60_000L && (d.visited || refreshDue(d, now))) {
                    for (var e : d.players.entrySet()) if (due(e.getValue(), now)) refreshPlayer(d, e.getKey(), e.getValue());
                    startRestore(d);
                }
                if (d.restoring) restore(level, d);
                if (!d.lootrClear.isEmpty() && t % 40 == 0) clearLootr(level, d);
            }
        }
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            boolean in = false;
            for (D d : DUNGEONS.values()) if (d.dim.equals(dim((ServerLevel) p.level())) && d.contains(p.blockPosition())) { in = true; break; }
            if (!in) INSIDE.remove(p.getUUID());
        }
        if (t % 20 == 0) {
            for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                String dim = dim((ServerLevel) p.level());
                D matched = null;
                for (D d : DUNGEONS.values()) {
                    if (d.dim.equals(dim) && d.containsColumn(p.blockPosition())) {
                        matched = d;
                        break;
                    }
                }
                String hudText = "";
                boolean inDungeon = matched != null;
                if (inDungeon) {
                    P pState = matched.players.get(p.getUUID());
                    if (pState == null || pState.firstOpenMs == 0) hudText = "Ready";
                    else {
                        long left = Math.max(0L, refreshMs() - (System.currentTimeMillis() - pState.firstOpenMs));
                        if (left <= 0) hudText = "Ready";
                        else hudText = left < 3_600_000L ? (left / 60_000L) + "m"
                                : left < 86_400_000L ? (left / 3_600_000L) + "h"
                                : (left / 86_400_000L) + "d " + ((left % 86_400_000L) / 3_600_000L) + "h";
                    }
                }
                String stateKey = inDungeon ? hudText : "";
                String prevKey = LAST_HUD.get(p.getUUID());
                if (!java.util.Objects.equals(stateKey, prevKey)) {
                    LAST_HUD.put(p.getUUID(), stateKey);
                    if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, DungeonHudPayload.TYPE)) {
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new DungeonHudPayload(inDungeon, hudText));
                    }
                }
            }
        }
        if (dirty && t % 600 == 0) save();
    }

    private static void enter(ServerPlayer player, D d) {
        if (d.key.equals(INSIDE.get(player.getUUID()))) return;
        INSIDE.put(player.getUUID(), d.key);
        P p = d.players.get(player.getUUID());
        String msg;
        if (p == null || p.firstOpenMs == 0) msg = "Unexplored: your loot is waiting";
        else {
            long left = Math.max(60_000L, refreshMs() - (System.currentTimeMillis() - p.firstOpenMs));
            String t = left < 3_600_000L ? left / 60_000L + "m"
                    : left < 86_400_000L ? left / 3_600_000L + "h"
                    : left / 86_400_000L + "d " + (left % 86_400_000L) / 3_600_000L + "h";
            msg = "Your loot here refreshes in " + t;
        }
        player.sendOverlayMessage(Component.literal(msg));
    }

    private static boolean due(P p, long now) { return p.firstOpenMs > 0 && now - p.firstOpenMs >= refreshMs(); }
    private static long refreshMs() { return LootTuning.lootRefreshDays * 86_400_000L; }
    private static boolean refreshDue(D d, long now) { for (P p : d.players.values()) if (due(p, now)) return true; return false; }

    private static void refreshPlayer(D d, UUID who, P p) {
        if (p.claimed.isEmpty() && p.firstOpenMs == 0) return;
        if (LootrBridge.loaded()) for (long c : p.claimed) d.lootrClear.add(who + "|" + c);
        p.cycle++;
        p.claimed.clear();
        p.firstOpenMs = 0;
        d.refillVanilla = true;
        dirty = true;
        ChestHints.markDirty(who);
    }

    private static void clearLootr(ServerLevel level, D d) {
        Iterator<String> it = d.lootrClear.iterator();
        int budget = 32;
        while (it.hasNext() && budget-- > 0) {
            String[] k = it.next().split("\\|");
            BlockPos pos = BlockPos.of(Long.parseLong(k[1]));
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) continue;
            LootrBridge.clearPlayerChest(level, UUID.fromString(k[0]), pos);
            it.remove();
            dirty = true;
        }
    }

    // ---------------------------------------------------------------- snapshot

    private static void capture(ServerLevel level, D d) {
        int budget = CAPTURE_BLOCKS_PER_TICK;
        if (d.snap == null) {
            d.snap = readSnapshot(d);
            d.captured.clear();                                         // trust the file, not the state json
            if (d.snap != null) for (Tag t : d.snap.getListOrEmpty("boxes")) d.captured.add(((CompoundTag) t).getIntOr("i", -1));
            d.paletteIdx = null;
            if (d.snap != null) { int k = 0; d.paletteIdx = new HashMap<>(); var bl = server.overworld().holderLookup(Registries.BLOCK);
                for (Tag t : d.snap.getListOrEmpty("palette")) d.paletteIdx.putIfAbsent(NbtUtils.readBlockState(bl, (CompoundTag) t), k++); }
        }
        for (int i = 0; i < d.boxes.size() && budget > 0; i++) {
            if (d.captured.contains(i)) continue;
            int[] b = d.boxes.get(i);
            if (!loaded(level, b)) continue;
            budget -= volume(b);
            int[] states = new int[volume(b)];
            ListTag bes = new ListTag();
            int n = 0;
            for (int x = b[1]; x <= b[4]; x++) for (int y = b[2]; y <= b[5]; y++) for (int z = b[3]; z <= b[6]; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                states[n++] = d.palette(level.getBlockState(pos));
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
                    tag.putInt("x", x); tag.putInt("y", y); tag.putInt("z", z);
                    bes.add(tag);
                }
            }
            CompoundTag box = new CompoundTag();
            box.putInt("i", i);
            box.putIntArray("s", states);
            box.put("be", bes);
            d.snapBoxes().add(box);
            d.captured.add(i);
            dirty = true;
        }
        if (d.snapshotDone()) { writeSnapshot(d); d.snap = null; Infinium.LOG.info("infinium: dungeon {} snapshot complete ({} rooms)", d.key, d.boxCount); }
    }

    private static boolean loaded(ServerLevel level, int[] b) {
        for (int cx = b[1] >> 4; cx <= b[4] >> 4; cx++) for (int cz = b[3] >> 4; cz <= b[6] >> 4; cz++)
            if (level.getChunkSource().getChunkNow(cx, cz) == null) return false;
        return true;
    }

    private static int volume(int[] b) { return (b[4] - b[1] + 1) * (b[5] - b[2] + 1) * (b[6] - b[3] + 1); }

    // ---------------------------------------------------------------- restore

    private static void startRestore(D d) {
        d.restoring = true;
        d.pending.clear();
        for (int i = 0; i < d.boxCount; i++) d.pending.add(i);
        d.cursor = 0;
        d.cursorBox = -1;
        dirty = true;
    }

    private static void restore(ServerLevel level, D d) {
        if (d.snap == null) d.snap = readSnapshot(d);
        if (d.snap == null) { d.restoring = false; d.captured.clear(); Infinium.LOG.warn("infinium: snapshot missing for {}, recapturing", d.key); return; }
        List<BlockState> palette = new ArrayList<>();
        var blocks = level.holderLookup(Registries.BLOCK);
        for (Tag t : d.snap.getListOrEmpty("palette")) palette.add(NbtUtils.readBlockState(blocks, (CompoundTag) t));
        Map<Integer, CompoundTag> byIndex = new HashMap<>();
        for (Tag t : d.snap.getListOrEmpty("boxes")) { CompoundTag c = (CompoundTag) t; byIndex.put(c.getIntOr("i", -1), c); }
        int changes = CHANGES_PER_TICK, compares = COMPARES_PER_TICK;
        Iterator<Integer> it = d.pending.iterator();
        while (it.hasNext() && changes > 0 && compares > 0) {
            int i = it.next();
            int[] b = i < d.boxes.size() ? d.boxes.get(i) : null;
            CompoundTag box = byIndex.get(i);
            if (b == null || box == null) { it.remove(); continue; }
            if (!loaded(level, b)) continue;                                   // finish when its chunks load
            int[] states = box.getIntArray("s").orElse(new int[0]);
            Map<Long, CompoundTag> bes = new HashMap<>();
            for (Tag t : box.getListOrEmpty("be")) { CompoundTag c = (CompoundTag) t; bes.put(BlockPos.asLong(c.getIntOr("x", 0), c.getIntOr("y", 0), c.getIntOr("z", 0)), c); }
            int n = 0, start = i == d.cursorBox ? d.cursor : 0;
            boolean done = true;
            outer:
            for (int x = b[1]; x <= b[4]; x++) for (int y = b[2]; y <= b[5]; y++) for (int z = b[3]; z <= b[6]; z++, n++) {
                if (n < start) continue;
                if (changes <= 0 || compares <= 0) { d.cursor = n; d.cursorBox = i; done = false; break outer; }
                compares--;
                if (n >= states.length) continue;
                BlockPos pos = new BlockPos(x, y, z);
                BlockState want = palette.get(Math.max(0, Math.min(palette.size() - 1, states[n])));
                BlockState cur = level.getBlockState(pos);
                CompoundTag beTag = bes.get(pos.asLong());
                if (cur == want) {
                    if (d.refillVanilla && beTag != null && level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity c
                            && !c.getClass().getName().startsWith("noobanidus.mods.lootr") && c.getLootTable() == null && dev.justin.infinium.loot.NaturalMark.isMarked(c)) {
                        relootVanilla(level, c, beTag);
                        changes--;
                    }
                    continue;
                }
                if (playerContainer(level, pos)) continue;               // R6d: never overwrite a player-placed container
                changes--;
                level.setBlock(pos, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                if (beTag != null) {
                    CompoundTag tag = beTag.copy();
                    boolean lootr = tag.getStringOr("id", "").startsWith("lootr:");
                    if (!lootr && !d.refillVanilla) { tag.remove("LootTable"); tag.remove("LootTableSeed"); }
                    if (!lootr && !tag.contains("LootTable") && !markedTag(tag)) tag.remove("Items");   // R6d: no item dupes from non-natural snapshot containers
                    BlockEntity be = BlockEntity.loadStatic(pos, want, tag, level.registryAccess());
                    if (be != null) level.setBlockEntity(be);
                }
            }
            if (done) { it.remove(); d.cursor = 0; d.cursorBox = -1; }
            else break;
        }
        if (d.pending.isEmpty()) {
            d.restoring = false;
            d.visited = false;
            d.refillVanilla = false;
            d.emptyMs = 0;
            d.snap = null;
            dirty = true;
            Infinium.LOG.info("infinium: dungeon {} rebuilt", d.key);
        }
    }

    /** A container block entity that is not a naturally generated / Lootr loot container. */
    private static boolean playerContainer(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof net.minecraft.world.Container && !dev.justin.infinium.loot.NaturalMark.natural(be);
    }

    private static boolean markedTag(CompoundTag tag) {
        return tag.getCompound("fabric:attachments").map(a -> a.contains("infinium:natural_loot")).orElse(false);
    }

    private static void relootVanilla(ServerLevel level, RandomizableContainerBlockEntity c, CompoundTag snap) {
        String table = snap.getStringOr("LootTable", "");
        if (table.isEmpty()) return;
        try {
            c.clearContent();
            c.setLootTable(net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE, net.minecraft.resources.Identifier.parse(table)));
            c.setLootTableSeed(level.getRandom().nextLong());
            c.setChanged();
        } catch (Exception ignored) {}
    }

    // ---------------------------------------------------------------- model + persistence

    private static String dim(ServerLevel level) { return level.dimension().identifier().toString(); }
    private static String key(ServerLevel level, RoguelikeCompat.Site s) { return dim(level) + "|" + s.siteKey(); }

    private static D dungeon(ServerLevel level, RoguelikeCompat.Site s) {
        String k = key(level, s);
        D d = DUNGEONS.get(k);
        if (d == null) { d = new D(k, dim(level)); DUNGEONS.put(k, d); dirty = true; }
        if (d.boxCount == 0 && s.isIndexed()) d.setBoxes(s.roomBoxes());
        return d;
    }

    private static D nearest(ServerLevel level, BlockPos at) {
        D best = null; double bd = Double.MAX_VALUE; String dim = dim(level);
        for (D d : DUNGEONS.values()) {
            if (!d.dim.equals(dim) || d.boxCount == 0) continue;
            double dx = (d.minX + d.maxX) / 2.0 - at.getX(), dz = (d.minZ + d.maxZ) / 2.0 - at.getZ(), dd = dx * dx + dz * dz;
            if (dd < bd) { bd = dd; best = d; }
        }
        return best;
    }

    private static Path root() { return server.getWorldPath(LevelResource.ROOT); }
    private static Path snapPath(D d) { return root().resolve("infinium_snapshots").resolve(d.key.replaceAll("[^A-Za-z0-9_.-]", "_") + ".nbt.gz"); }

    private static CompoundTag readSnapshot(D d) {
        Path f = snapPath(d);
        if (!Files.exists(f)) return null;
        try { return NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap()); }
        catch (Exception e) { Infinium.LOG.warn("infinium: snapshot unreadable {}: {}", f, e.toString()); return null; }
    }

    private static void writeSnapshot(D d) {
        if (d.snap == null) return;
        try { Path f = snapPath(d); Files.createDirectories(f.getParent()); NbtIo.writeCompressed(d.snap, f); }
        catch (Exception e) { Infinium.LOG.warn("infinium: snapshot write failed {}: {}", d.key, e.toString()); }
    }

    /** Partial captures survive a restart. */
    private static void flushSnapshots() { for (D d : DUNGEONS.values()) if (d.snap != null && !d.snapshotDone()) writeSnapshot(d); }

    private static void load() {
        DUNGEONS.clear();
        Path f = root().resolve("infinium_dungeon_state.json");
        if (!Files.exists(f)) return;
        try {
            JsonObject rootJ = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var en : rootJ.entrySet()) {
                JsonObject o = en.getValue().getAsJsonObject();
                D d = new D(en.getKey(), o.get("dim").getAsString());
                List<int[]> boxes = new ArrayList<>();
                for (var b : o.getAsJsonArray("boxes")) { JsonArray a = b.getAsJsonArray(); int[] v = new int[7]; for (int i = 0; i < 7; i++) v[i] = a.get(i).getAsInt(); boxes.add(v); }
                d.setBoxes(boxes);
                for (var c : o.getAsJsonArray("captured")) d.captured.add(c.getAsInt());
                d.emptyMs = o.has("emptyMs") ? o.get("emptyMs").getAsLong() : 0;
                d.visited = o.has("visited") && o.get("visited").getAsBoolean();
                d.refillVanilla = o.has("refillVanilla") && o.get("refillVanilla").getAsBoolean();
                if (o.has("restoring") && o.get("restoring").getAsBoolean()) startRestore(d);
                if (o.get("lootrClear") instanceof JsonArray lc) for (var x : lc) d.lootrClear.add(x.getAsString());
                for (var pe : o.getAsJsonObject("players").entrySet()) {
                    JsonObject po = pe.getValue().getAsJsonObject();
                    P p = new P();
                    p.cycle = po.get("cycle").getAsInt();
                    p.firstOpenMs = po.get("firstOpenMs").getAsLong();
                    for (var x : po.getAsJsonArray("claimed")) p.claimed.add(x.getAsLong());
                    d.players.put(UUID.fromString(pe.getKey()), p);
                }
                DUNGEONS.put(d.key, d);
            }
        } catch (Exception e) { Infinium.LOG.warn("infinium: dungeon state unreadable: {}", e.toString()); }
    }

    private static void save() {
        if (server == null) return;
        try {
            JsonObject rootJ = new JsonObject();
            for (D d : DUNGEONS.values()) {
                JsonObject o = new JsonObject();
                o.addProperty("dim", d.dim);
                JsonArray boxes = new JsonArray();
                for (int[] b : d.boxes) { JsonArray a = new JsonArray(); for (int v : b) a.add(v); boxes.add(a); }
                o.add("boxes", boxes);
                JsonArray cap = new JsonArray(); for (int c : d.captured) cap.add(c); o.add("captured", cap);
                o.addProperty("emptyMs", d.emptyMs);
                o.addProperty("visited", d.visited);
                o.addProperty("restoring", d.restoring);
                o.addProperty("refillVanilla", d.refillVanilla);
                JsonArray lc = new JsonArray(); for (String s : d.lootrClear) lc.add(s); o.add("lootrClear", lc);
                JsonObject players = new JsonObject();
                for (var pe : d.players.entrySet()) {
                    JsonObject po = new JsonObject();
                    po.addProperty("cycle", pe.getValue().cycle);
                    po.addProperty("firstOpenMs", pe.getValue().firstOpenMs);
                    JsonArray cl = new JsonArray(); for (long c : pe.getValue().claimed) cl.add(c); po.add("claimed", cl);
                    players.add(pe.getKey().toString(), po);
                }
                o.add("players", players);
                rootJ.add(d.key, o);
            }
            Files.writeString(root().resolve("infinium_dungeon_state.json"), rootJ.toString(), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Exception e) { Infinium.LOG.warn("infinium: could not save dungeon state: {}", e.toString()); }
    }

    private static final class D {
        final String key, dim;
        final List<int[]> boxes = new ArrayList<>();
        int boxCount, minX, minY, minZ, maxX, maxY, maxZ;
        final Set<Integer> captured = new HashSet<>();
        final Map<UUID, P> players = new HashMap<>();
        final Set<String> lootrClear = new LinkedHashSet<>();
        final LinkedHashSet<Integer> pending = new LinkedHashSet<>();
        long emptyMs; boolean occupied, visited, restoring, refillVanilla; int cursor, cursorBox = -1;
        CompoundTag snap;                       // in memory while capturing / restoring only
        transient Map<BlockState, Integer> paletteIdx;

        D(String key, String dim) { this.key = key; this.dim = dim; }

        void setBoxes(List<int[]> src) {
            boxes.clear();
            for (int[] b : src) boxes.add(b.clone());
            boxCount = boxes.size();
            minX = minY = minZ = Integer.MAX_VALUE; maxX = maxY = maxZ = Integer.MIN_VALUE;
            for (int[] b : boxes) {
                minX = Math.min(minX, b[1]); minY = Math.min(minY, b[2]); minZ = Math.min(minZ, b[3]);
                maxX = Math.max(maxX, b[4]); maxY = Math.max(maxY, b[5]); maxZ = Math.max(maxZ, b[6]);
            }
        }
        boolean snapshotDone() { return boxCount > 0 && captured.size() >= boxCount; }
        boolean contains(BlockPos p) {
            if (boxCount == 0 || p.getX() < minX - 2 || p.getX() > maxX + 2 || p.getZ() < minZ - 2 || p.getZ() > maxZ + 2 || p.getY() < minY - 2 || p.getY() > maxY + 4) return false;
            for (int[] b : boxes) if (p.getX() >= b[1] - 2 && p.getX() <= b[4] + 2 && p.getY() >= b[2] - 2 && p.getY() <= b[5] + 2 && p.getZ() >= b[3] - 2 && p.getZ() <= b[6] + 2) return true;
            return false;
        }
        boolean containsColumn(BlockPos p) {
            if (boxCount == 0 || p.getX() < minX - 16 || p.getX() > maxX + 16 || p.getZ() < minZ - 16 || p.getZ() > maxZ + 16) return false;
            return true;
        }
        ListTag snapBoxes() {
            if (snap == null) snap = new CompoundTag();
            if (!(snap.get("boxes") instanceof ListTag)) snap.put("boxes", new ListTag());
            return (ListTag) snap.get("boxes");
        }
        int palette(BlockState s) {
            if (snap == null) snap = new CompoundTag();
            if (!(snap.get("palette") instanceof ListTag)) snap.put("palette", new ListTag());
            ListTag pal = (ListTag) snap.get("palette");
            if (paletteIdx == null) paletteIdx = new HashMap<>();
            Integer i = paletteIdx.get(s);
            if (i != null) return i;
            pal.add(NbtUtils.writeBlockState(s));
            paletteIdx.put(s, pal.size() - 1);
            return pal.size() - 1;
        }
    }

    private static final class P { int cycle; long firstOpenMs; final Set<Long> claimed = new LinkedHashSet<>(); }

    private DungeonReset() {}
}
