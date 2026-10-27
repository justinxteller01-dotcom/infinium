package dev.justin.infinium.loot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.StyleLoot;
import dev.justin.infinium.dungeon.DungeonReset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * R6b. Non-dungeon chests only. First roll (vanilla or Lootr per-player) gets the cycle-0 share (MODIFY_DROPS).
 * Each player+chest then gets a deterministic random 4-7 REAL day timer (epoch millis; works for unloaded chunks and any
 * dimension, nothing ticks per chest). When due, the next share is generated INTO the chest: that player's Lootr inventory,
 * or the vanilla chest's free slots. Triggered lazily: ChestHints' view scan (every ~5 s, loaded chunks in view distance,
 * plus a scan soon after chunk loads) and, as a fallback, on open. Never inserts into the player inventory, never messages.
 * If the items do not fit, the refill stays due and is retried. Vanilla loot is never rewritten.
 */
public final class NaturalRefill {
    private static final Map<String, ChestMem> CHESTS = new HashMap<>();
    private static MinecraftServer server;
    private static boolean dirty;
    private static final long DAY_MS = 86_400_000L;

    public static void register() {
        LootTuning.load();
        // javap 26.2: ModifyDrops.modifyLootTableDrops(Holder<LootTable>, LootContext, List<ItemStack>)
        LootTableEvents.MODIFY_DROPS.register(NaturalRefill::onDrops);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)) return InteractionResult.PASS;
            if (player.isSpectator()) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof RandomizableContainerBlockEntity box)) return InteractionResult.PASS;
            resetIfDue(sp, level, pos, be);             // fallback to the view scan; runs before the menu opens/rolls
            return InteractionResult.PASS;
        });
        ServerLifecycleEvents.SERVER_STARTED.register(s -> { server = s; load(s); });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> save(s));
    }

    private static void onDrops(net.minecraft.core.Holder<LootTable> holder, LootContext context, List<net.minecraft.world.item.ItemStack> drops) {
        var key = holder.unwrapKey().orElse(null);
        if (key == null) return;
        Identifier id = key.identifier();
        StyleLoot.Tier tier = StyleLoot.tierFor(id);
        if (tier == null) return;                       // roguelike tables: DungeonLoot (StyleLoot MODIFY_DROPS)
        Entity entity = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
        Vec3 origin = context.getOptionalParameter(LootContextParams.ORIGIN);
        if (!(entity instanceof ServerPlayer player) || origin == null || !(player.level() instanceof ServerLevel level)) {
            drops.addAll(ShareRoll.natural(context.getRandom().nextLong(), tier).items());
            return;
        }
        BlockPos pos = BlockPos.containing(origin);
        BlockEntity rolled = level.getBlockEntity(pos);
        if (rolled instanceof RandomizableContainerBlockEntity && NaturalMark.mark(rolled)) {   // rolling a loot table = natural
            var ch = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);         // no load: already loaded here
            if (ch != null) ch.markUnsaved();
        }
        ChestMem mem = CHESTS.computeIfAbsent(key(level, player.getUUID(), pos), k -> new ChestMem());
        mem.table = id.toString();
        long now = System.currentTimeMillis();
        if (mem.pending) {                              // R6c full re-roll after a reset
            drops.addAll(ShareRoll.natural(ShareSeed.natural(level.getSeed(), pos, player.getUUID(), mem.cycle), tier).items());
        } else if (mem.refillAtMs == 0) {
            drops.addAll(ShareRoll.natural(ShareSeed.natural(level.getSeed(), pos, player.getUUID(), mem.cycle), tier).items());
        } else if (now >= mem.refillAtMs) {
            mem.cycle++;
            drops.addAll(ShareRoll.natural(ShareSeed.natural(level.getSeed(), pos, player.getUUID(), mem.cycle), tier).items());
        } else return;
        stamp(level, player.getUUID(), pos, mem, now);
        mem.pending = false;                            // rolled at open time: player is looking at it now
        ChestHints.markDirty(player.getUUID());
    }

    /**
     * R6c reset: when this player's timer is due, wipe their copy of the chest and arm a FULL re-roll (whole loot table +
     * Infinium share, paid in onDrops on the next roll). Lootr: clear the player's per-player inventory (Lootr re-rolls on
     * open). Vanilla tracked chest: clear contents and restore its loot table (shared chest: affects everyone).
     * The cycle advances and a new 4-7 real day timer starts now, so it cannot be farmed.
     */
    public static boolean resetIfDue(ServerPlayer player, ServerLevel level, BlockPos pos, BlockEntity be) {
        ChestMem mem = CHESTS.get(key(level, player.getUUID(), pos));
        if (mem == null || mem.table == null || mem.refillAtMs == 0) return false;
        long now = System.currentTimeMillis();
        if (now < mem.refillAtMs) return false;
        Identifier table;
        try { table = Identifier.parse(mem.table); } catch (Exception e) { return false; }
        if (StyleLoot.tierFor(table) == null) return false;
        if (!NaturalMark.natural(be)) {                  // R6d: not the generated chest (player-placed / replaced): drop stale tracking
            CHESTS.remove(key(level, player.getUUID(), pos));
            dirty = true;
            ChestHints.markDirty(player.getUUID());
            return false;
        }
        if (LootrBridge.isLootr(be)) {
            if (LootrBridge.playerInventory(be, player.getUUID()) != null && !LootrBridge.clearPlayerChest(level, player.getUUID(), pos)) return false;
        } else if (be instanceof RandomizableContainerBlockEntity box) {
            if (box.getLootTable() == null) {
                for (int i = 0; i < box.getContainerSize(); i++) box.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                box.setLootTable(ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, table), level.getRandom().nextLong());
                box.setChanged();
            }
        } else return false;
        mem.cycle++;
        stamp(level, player.getUUID(), pos, mem, now);
        mem.pending = true;                              // re-roll armed: next roll for this player pays this cycle's share
        ChestHints.markDirty(player.getUUID());
        return true;
    }

    private static void stamp(ServerLevel level, UUID who, BlockPos pos, ChestMem mem, long now) {
        mem.lootMs = now;
        mem.refillAtMs = now + refillMs(level, who, pos, mem.cycle);
        mem.opened = true;
        dirty = true;
    }

    private static net.minecraft.world.item.ItemStack insert(Container container, net.minecraft.world.item.ItemStack stack) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) { container.setItem(i, stack); container.setChanged(); return net.minecraft.world.item.ItemStack.EMPTY; }
        }
        return stack;
    }

    /** Opened this cycle and the refill is not yet due. */
    static boolean opened(ServerLevel level, UUID player, BlockPos pos) {
        ChestMem mem = CHESTS.get(key(level, player, pos));
        return mem != null && mem.opened && (mem.refillAtMs == 0 || System.currentTimeMillis() < mem.refillAtMs);
    }

    static boolean due(ServerLevel level, UUID player, BlockPos pos) {
        ChestMem mem = CHESTS.get(key(level, player, pos));
        return mem != null && mem.refillAtMs > 0 && System.currentTimeMillis() >= mem.refillAtMs;
    }

    /** Reset done, full re-roll armed but not rolled yet for this player. */
    static boolean pending(ServerLevel level, UUID player, BlockPos pos) {
        ChestMem mem = CHESTS.get(key(level, player, pos));
        return mem != null && mem.pending;
    }

    static int seedCycle(ServerLevel level, UUID player, BlockPos pos) {
        ChestMem mem = CHESTS.get(key(level, player, pos));
        if (mem == null) return 0;
        if (mem.refillAtMs > 0 && System.currentTimeMillis() >= mem.refillAtMs) return mem.cycle + 1;
        return mem.cycle;
    }

    static String tableOf(ServerLevel level, BlockPos pos) {
        for (var en : CHESTS.entrySet()) if (en.getKey().endsWith(posKey(level, pos))) return en.getValue().table;
        return null;
    }

    static void rememberTable(ServerLevel level, BlockPos pos, Identifier table) {
        if (table == null || "roguelike".equals(table.getNamespace())) return;
        if (StyleLoot.tierFor(table) == null) return;
        String tk = "table|" + posKey(level, pos);
        ChestMem mem = CHESTS.computeIfAbsent(tk, k -> new ChestMem());
        if (table.toString().equals(mem.table)) return;
        mem.table = table.toString();
        dirty = true;
    }

    /** R6c: any container table (incl. roguelike) for dungeon vanilla resets. */
    static void rememberAnyTable(ServerLevel level, BlockPos pos, Identifier table) {
        if (table == null) return;
        ChestMem mem = CHESTS.computeIfAbsent("dtable|" + posKey(level, pos), k -> new ChestMem());
        if (table.toString().equals(mem.table)) return;
        mem.table = table.toString();
        dirty = true;
    }

    public static Identifier rememberedAnyTable(ServerLevel level, BlockPos pos) {
        ChestMem mem = CHESTS.get("dtable|" + posKey(level, pos));
        if (mem == null || mem.table == null) return null;
        try { return Identifier.parse(mem.table); } catch (Exception e) { return null; }
    }

    static Identifier rememberedTable(ServerLevel level, BlockPos pos) {
        ChestMem mem = CHESTS.get("table|" + posKey(level, pos));
        if (mem == null || mem.table == null) return null;
        try { return Identifier.parse(mem.table); } catch (Exception e) { return null; }
    }

    /** Deterministic random duration in [min, max] real days, seeded by player + dimension + chest pos (+ cycle). */
    private static long refillMs(ServerLevel level, UUID who, BlockPos pos, int cycle) {
        long lo = Math.max(1L, LootTuning.refillMinRealDays) * DAY_MS;
        long hi = Math.max(LootTuning.refillMinRealDays, LootTuning.refillMaxRealDays) * DAY_MS;
        long h = who.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ who.getLeastSignificantBits();
        h = h * 31 + level.dimension().identifier().toString().hashCode();
        h = h * 31 + pos.asLong();
        h = h * 31 + cycle;
        h ^= (h >>> 33); h *= 0xff51afd7ed558ccdL; h ^= (h >>> 33); h *= 0xc4ceb9fe1a85ec53L; h ^= (h >>> 33);
        return lo + Math.floorMod(h, hi - lo + 1);
    }

    private static long migrateMs(String k) {
        long lo = Math.max(1L, LootTuning.refillMinRealDays) * DAY_MS;
        long hi = Math.max(LootTuning.refillMinRealDays, LootTuning.refillMaxRealDays) * DAY_MS;
        return lo + Math.floorMod((long) k.hashCode() * 0x9E3779B97F4A7C15L, hi - lo + 1);
    }

    private static String key(ServerLevel level, UUID player, BlockPos pos) {
        return player + "|" + posKey(level, pos);
    }

    private static String posKey(ServerLevel level, BlockPos pos) {
        return level.dimension().identifier() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    private static void load(MinecraftServer server) {
        CHESTS.clear();
        try {
            Path f = file(server);
            if (!Files.exists(f)) return;
            JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var en : root.entrySet()) {
                if (!(en.getValue() instanceof JsonObject o)) continue;
                ChestMem mem = new ChestMem();
                if (o.has("cycle")) mem.cycle = o.get("cycle").getAsInt();
                if (o.has("refillAtMs")) mem.refillAtMs = o.get("refillAtMs").getAsLong();
                if (o.has("lootMs")) mem.lootMs = o.get("lootMs").getAsLong();
                if (o.has("pending")) mem.pending = o.get("pending").getAsBoolean();
                if (!o.has("refillAtMs") && o.has("refillAt") && o.get("refillAt").getAsLong() > 0) {   // R6 game-tick state: restart timer
                    mem.lootMs = System.currentTimeMillis(); mem.migrate = true; dirty = true;
                }
                if (o.has("opened")) mem.opened = o.get("opened").getAsBoolean();
                if (o.has("table")) mem.table = o.get("table").getAsString();
                if (mem.migrate) mem.refillAtMs = mem.lootMs + migrateMs(en.getKey());
                CHESTS.put(en.getKey(), mem);
            }
        } catch (Exception e) {
            Infinium.LOG.warn("natural refill state unreadable: {}", e.toString());
        }
    }

    public static void saveTick() {
        if (dirty && server != null) save(server);
    }

    private static void save(MinecraftServer server) {
        try {
            JsonObject root = new JsonObject();
            for (var en : CHESTS.entrySet()) {
                JsonObject o = new JsonObject();
                o.addProperty("cycle", en.getValue().cycle);
                o.addProperty("refillAtMs", en.getValue().refillAtMs);
                o.addProperty("lootMs", en.getValue().lootMs);
                o.addProperty("pending", en.getValue().pending);
                o.addProperty("opened", en.getValue().opened);
                if (en.getValue().table != null) o.addProperty("table", en.getValue().table);
                root.add(en.getKey(), o);
            }
            Path f = file(server);
            Files.createDirectories(f.getParent());
            Files.writeString(f, root.toString(), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Exception e) {
            Infinium.LOG.warn("could not save natural refill state: {}", e.toString());
        }
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("infinium_natural_refill.json");
    }

    private static final class ChestMem {
        int cycle;
        long refillAtMs, lootMs;
        boolean opened, pending, migrate;
        String table;
    }

    private NaturalRefill() {}
}
