package dev.justin.infinium;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Roguelike Dungeons integration (optional). Remembers every dungeon the mod lays out (its own state forgets a dungeon once
 * fully generated) for the Dungeon Finder, and indexes each dungeon's loot containers (position + floor, counted from the
 * top, 1-based, over the real 20-room floors; stairway-only floors above sea level count as floor 1) WITHOUT touching them.
 * Infinium loot is then added when a container's roguelike:chests/* table is actually rolled (StyleLoot MODIFY_DROPS hook),
 * so Lootr's per-player rolls include it. See DungeonLoot for the per-dungeon budget.
 * Size class from rooms per real floor: < largeRoomsPerFloor normal, < xlRoomsPerFloor large, else extra large.
 * Persisted per world+dimension in <world>/infinium_dungeons_<dim>.json.
 */
public final class RoguelikeCompat {
    public static double LARGE_ROOMS_PER_FLOOR = 15, XL_ROOMS_PER_FLOOR = 18;   // config: roguelike.largeRoomsPerFloor / xlRoomsPerFloor
    static final int REAL_FLOOR_MIN_ROOMS = 3;
    public static final class Site {
        int x, z, topY = 100, size, rooms; boolean generated, looted, topKnown;
        boolean indexed; int floorCount; double roomsPerFloor;
        List<Long> chestPos = new ArrayList<>(); List<Integer> chestFloor = new ArrayList<>();
        List<int[]> boxes = new ArrayList<>();   // {floorY, x0,y0,z0,x1,y1,z1}
        transient Map<Long, Integer> chestIdx;
        /** R6 read-only views for dungeon reset / chest hints (dev.justin.infinium.dungeon, .loot). */
        public boolean isIndexed() { return indexed && generated; }
        public List<int[]> roomBoxes() { return Collections.unmodifiableList(boxes); }
        public List<Long> chests() { return Collections.unmodifiableList(chestPos); }
        public int floors() { return floorCount; }
        public double roomsPerFloorValue() { return roomsPerFloor; }
        public String siteKey() { return x + "," + z; }
        public int x() { return x; } public int z() { return z; } public int topY() { return topY; } public int size() { return size; }
        /** Real floors (>= 3 rooms), descending Y. */
        List<Integer> realFloors() {
            Map<Integer, Integer> c = new TreeMap<>(Comparator.reverseOrder());
            for (int[] b : boxes) c.merge(b[0], 1, Integer::sum);
            List<Integer> out = new ArrayList<>();
            for (var e : c.entrySet()) if (e.getValue() >= REAL_FLOOR_MIN_ROOMS) out.add(e.getKey());
            if (out.isEmpty()) out.addAll(c.keySet());
            return out;
        }
        int floorOfBoxY(List<Integer> real, int y) {
            for (int i = 0; i < real.size(); i++) if (real.get(i) <= y) return i + 1;
            return Math.max(1, real.size());
        }
        int[] boxAt(BlockPos p) {
            for (int[] b : boxes) if (p.getX() >= b[1] && p.getX() <= b[4] && p.getY() >= b[2] && p.getY() <= b[5] && p.getZ() >= b[3] && p.getZ() <= b[6]) return b;
            return null;
        }
        void classify() {
            List<Integer> real = realFloors();
            floorCount = Math.max(1, real.size());
            int rr = 0; for (int[] b : boxes) if (real.contains(b[0])) rr++;
            roomsPerFloor = rr / (double) floorCount;
            size = roomsPerFloor >= XL_ROOMS_PER_FLOOR ? 2 : roomsPerFloor >= LARGE_ROOMS_PER_FLOOR ? 1 : 0;
        }
    }
    private static final Map<String, Map<Long, Site>> SITES = new HashMap<>();
    private static final Set<String> DIRTY = new HashSet<>();
    private static net.minecraft.server.MinecraftServer SERVER;
    static boolean present() { return FabricLoader.getInstance().isModLoaded("roguelike"); }
    static String dim(ServerLevel l) { return l.dimension().identifier().toString().replace(':', '_').replace('/', '_'); }
    static Path file(String d) { return SERVER.getWorldPath(LevelResource.ROOT).resolve("infinium_dungeons_" + d + ".json"); }

    static Map<Long, Site> map(ServerLevel l) {
        return SITES.computeIfAbsent(dim(l), d -> {
            Map<Long, Site> m = new LinkedHashMap<>();
            try { Path p = file(d); if (Files.exists(p)) {
                List<Site> ls = new Gson().fromJson(Files.readString(p), new TypeToken<List<Site>>() {}.getType());
                if (ls != null) for (Site s : ls) m.put(key(s.x, s.z), s); } }
            catch (Exception e) { Infinium.LOG.warn("infinium: could not read dungeon index", e); }
            return m;
        });
    }
    static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }
    public static List<Site> sites(ServerLevel l) { return SERVER == null || !present() ? List.of() : new ArrayList<>(map(l).values()); }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> { SERVER = s; SITES.clear(); });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { save(); SERVER = null; SITES.clear(); });
        if (!present()) return;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int t = server.getTickCount();
            if (t % 40 != 0) return;
            for (ServerLevel l : server.getAllLevels()) {
                try { poll(l); } catch (Throwable e) { if (t % 6000 == 0) Infinium.LOG.warn("infinium: roguelike poll failed: " + e); }
                Map<Long, Site> m = map(l);
                for (Site s : m.values()) {
                    if (!s.topKnown && l.hasChunk(s.x >> 4, s.z >> 4) && s.generated) {
                        s.topY = l.getHeight(Heightmap.Types.WORLD_SURFACE, s.x, s.z); s.topKnown = true; DIRTY.add(dim(l)); }
                    if (s.generated && !s.indexed && tryIndex(l, s)) { s.indexed = true; DIRTY.add(dim(l)); }
                }
            }
            if (t % 1200 == 0) save();
        });
    }

    static void poll(ServerLevel l) throws Exception {
        Map<Long, Site> m = map(l);
        Set<Long> seen = new HashSet<>();
        for (RoguelikeBridge.Snap sn : RoguelikeBridge.poll(l)) {
            long k = key(sn.x(), sn.z()); seen.add(k);
            Site s = m.get(k);
            if (s == null) { s = new Site(); s.x = sn.x(); s.z = sn.z(); m.put(k, s); DIRTY.add(dim(l)); }
            if (!sn.rooms().isEmpty() && sn.rooms().size() != s.rooms) {
                s.rooms = sn.rooms().size();
                s.boxes.clear();
                for (var r : sn.rooms()) s.boxes.add(new int[]{r.y(), r.x0(), r.y0(), r.z0(), r.x1(), r.y1(), r.z1()});
                s.classify(); s.indexed = false; s.chestPos.clear(); s.chestFloor.clear(); s.chestIdx = null;
                DIRTY.add(dim(l));
            }
            if (sn.generated() && !s.generated) { s.generated = true; DIRTY.add(dim(l)); }
        }
        for (Site s : m.values()) if (!s.generated && !s.boxes.isEmpty() && !seen.contains(key(s.x, s.z))) { s.generated = true; DIRTY.add(dim(l)); } // dropped from roguelike state = done
    }

    /** Index the dungeon's loot containers (no unpacking, nothing written into them). True when done; false = some chunk
     *  not loaded yet, retry later. A container counts if it still has a roguelike loot table (vanilla) or is a Lootr container
     *  whose stored table is a roguelike one. */
    static boolean tryIndex(ServerLevel l, Site s) {
        if (s.boxes.isEmpty()) return true;
        for (int[] b : s.boxes)
            for (int cx = b[1] >> 4; cx <= b[4] >> 4; cx++) for (int cz = b[3] >> 4; cz <= b[6] >> 4; cz++) if (!l.hasChunk(cx, cz)) return false;
        if (s.floorCount == 0) s.classify();
        List<Integer> real = s.realFloors();
        Set<Long> seen = new HashSet<>();
        s.chestPos.clear(); s.chestFloor.clear(); s.chestIdx = null;
        for (int[] b : s.boxes) {
            int floor = s.floorOfBoxY(real, b[0]);
            for (BlockPos p : BlockPos.betweenClosed(b[1], b[2], b[3], b[4], b[5], b[6])) {
                BlockEntity be = l.getBlockEntity(p);
                if (!(be instanceof RandomizableContainerBlockEntity c) || !seen.add(p.asLong())) continue;
                if (!isRoguelikeTable(tableOf(c))) continue;
                s.chestPos.add(p.asLong()); s.chestFloor.add(floor);
            }
        }
        return true;
    }
    static net.minecraft.resources.Identifier tableOf(RandomizableContainerBlockEntity c) {
        var lt = c.getLootTable();
        if (lt != null) return lt.identifier();
        if (c.getClass().getName().startsWith("noobanidus.mods.lootr")) {      // Lootr keeps the original table in its own data
            try { Object k = c.getClass().getMethod("getDataLootTable").invoke(c);
                  if (k instanceof net.minecraft.resources.ResourceKey<?> rk) return rk.identifier(); } catch (Throwable ignored) {}
        }
        return null;
    }
    static boolean isRoguelikeTable(net.minecraft.resources.Identifier id) {
        return id != null && id.getNamespace().equals("roguelike") && id.getPath().startsWith("chests/");
    }
    /** R6: public lookup for dungeon reset / hints. */
    public static Site findSite(ServerLevel l, BlockPos pos) { return siteAt(l, pos); }
    public static net.minecraft.resources.Identifier containerTable(RandomizableContainerBlockEntity c) { return tableOf(c); }
    public static boolean roguelikeTable(net.minecraft.resources.Identifier id) { return isRoguelikeTable(id); }
    /** The site whose room boxes contain pos (same dimension), or null. */
    static Site siteAt(ServerLevel l, BlockPos pos) {
        if (SERVER == null || !present()) return null;
        for (Site s : map(l).values()) {
            if (Math.abs(s.x - pos.getX()) > 400 || Math.abs(s.z - pos.getZ()) > 400) continue;
            if (s.boxAt(pos) != null) return s;
        }
        return null;
    }
    static int floorAt(Site s, BlockPos pos) {
        int[] b = s.boxAt(pos);
        return b == null ? 1 : s.floorOfBoxY(s.realFloors(), b[0]);
    }
    static void save() {
        if (SERVER == null) return;
        for (String d : new ArrayList<>(DIRTY)) {
            Map<Long, Site> m = SITES.get(d); if (m == null) continue;
            try { Files.writeString(file(d), new Gson().toJson(new ArrayList<>(m.values()))); DIRTY.remove(d); }
            catch (Exception e) { Infinium.LOG.warn("infinium: could not save dungeon index", e); }
        }
    }
    private RoguelikeCompat() {}
}
