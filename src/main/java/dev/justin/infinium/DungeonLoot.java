package dev.justin.infinium;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Per-dungeon Infinium budget for Roguelike Dungeons (R5c). Expected yield per dungeon, PER PLAYER, is fixed by config and
 * does not depend on the chest count: each (dungeon, player) gets a deterministic plan (seed = world seed ^ dungeon ^ player
 * UUID) that rolls the dungeon totals and spreads them over the dungeon's indexed containers, floor weighted by depth.
 * When a roguelike:chests/* table is rolled at a container (vanilla first open, or each player's Lootr roll), the items
 * planned for that container and that player are appended to the drops (StyleLoot MODIFY_DROPS).
 * Without a player in the loot context (hopper, explosion) the plan for the nil UUID is used.
 * If the dungeon has not been indexed yet (its rooms were never all loaded at once), a per-container fallback roll with
 * the same expected value per floor is used (assumes FALLBACK_CHESTS_PER_FLOOR containers per floor).
 * No Spacetime Crystals and no Infinity templates are placed.
 */
public final class DungeonLoot {
    // ---- config (config/infinium_dungeon_loot.json -> "roguelike") ----
    public static int FRAGMENTS_MIN_NORMAL = 15;                   // 15..25 inclusive (mean 20.0); 40 normal dungeons = 800 fragments = 100 crystals
    public static int FRAGMENTS_MAX_NORMAL = 25;
    public static double FRAGMENTS_PER_NORMAL_DUNGEON = 20.0;      // expected mean (used by fallback + legacy config)
    public static double TIER1_TEMPLATES_PER_NORMAL_DUNGEON = 0.85;// ~11-13 templates in 10-15 dungeons (all 8 Astral/Cybernetic)
    public static double RARE_TEMPLATES_PER_NORMAL_DUNGEON = 0.42; // ~11-13 templates in 25-30 dungeons (all 8 Singularity/CyberPsycho)
    public static double[] SIZE_MULT = {1.0, 1.5, 2.5};           // normal, large, extra large
    public static double FRAGMENT_DEPTH_BIAS = 1.0, TIER1_DEPTH_BIAS = 0.5, RARE_DEPTH_BIAS = 4.0; // bottom floor weight = 1 + bias (top = 1)
    public static double FRAGMENT_SPREAD = 2.0 / 3.0;             // legacy spread knob (6..12 around mean 9)
    public static double FALLBACK_CHESTS_PER_FLOOR = 6.0;

    private static final Map<String, Map<Long, List<ItemStack>>> CACHE = new LinkedHashMap<>(64, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Map<Long, List<ItemStack>>> e) { return size() > 512; }
    };

    /** MODIFY_DROPS entry point for roguelike chest tables. */
    static void onDrops(LootContext ctx, List<ItemStack> drops) {
        Vec3 o = ctx.getOptionalParameter(LootContextParams.ORIGIN);
        if (o == null) return;
        ServerLevel l = ctx.getLevel();
        BlockPos pos = BlockPos.containing(o);
        RoguelikeCompat.Site s = RoguelikeCompat.siteAt(l, pos);
        Entity e = ctx.getOptionalParameter(LootContextParams.THIS_ENTITY);
        UUID who = e != null ? e.getUUID() : new UUID(0, 0);
        // R6: one share per player per chest per reset cycle (stops re-rolls after a dungeon rebuild from paying out again)
        if (e instanceof net.minecraft.server.level.ServerPlayer sp) {
            if (!dev.justin.infinium.dungeon.DungeonReset.claim(l, s, sp, pos)) return;
        }
        drops.addAll(share(l, s, pos, who));
    }

    /** R6: exactly what onDrops appends for this player at this container in the current cycle (chest hints use this). */
    public static List<ItemStack> preview(ServerLevel l, BlockPos pos, UUID who) {
        return share(l, RoguelikeCompat.siteAt(l, pos), pos, who);
    }

    private static List<ItemStack> share(ServerLevel l, RoguelikeCompat.Site s, BlockPos pos, UUID who) {
        List<ItemStack> drops = new ArrayList<>();
        int cycle = s == null ? 0 : dev.justin.infinium.dungeon.DungeonReset.cycle(l, s, who);
        if (s != null && s.indexed && !s.chestPos.isEmpty()) {
            if (s.chestIdx == null) { s.chestIdx = new HashMap<>(); for (int i = 0; i < s.chestPos.size(); i++) s.chestIdx.put(s.chestPos.get(i), i); }
            if (s.chestIdx.containsKey(pos.asLong())) {
                for (ItemStack st : plan(l, s, who, cycle).getOrDefault(pos.asLong(), List.of())) drops.add(st.copy());
                return drops;
            }
        }
        fallback(l, s, pos, who, cycle, drops);
        return drops;
    }
    /** Cycle 0 keeps the R5c seed exactly; later reset cycles get a new deterministic plan. */
    static long cycleMix(int cycle) { return cycle == 0 ? 0L : cycle * 0x94D049BB133111EBL; }

    static long seed(ServerLevel l, int sx, int sz, UUID who) {
        return l.getSeed() ^ (((long) sx << 32) ^ (sz & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L ^ who.getMostSignificantBits() * 31 ^ who.getLeastSignificantBits() ^ 0x1F1A1L;
    }
    static double weight(int floor, int floors, double bias) { return 1.0 + bias * (floors <= 1 ? 1.0 : (floor - 1) / (double) (floors - 1)); }
    static int sround(double m, RandomSource r) { int n = (int) Math.floor(m); return n + (r.nextDouble() < m - n ? 1 : 0); }

    static Map<Long, List<ItemStack>> plan(ServerLevel l, RoguelikeCompat.Site s, UUID who, int cycle) {
        String k = l.dimension().identifier() + "|" + s.x + "," + s.z + "|" + who + "|" + s.chestPos.size() + "|" + cycle;
        Map<Long, List<ItemStack>> m = CACHE.get(k);
        if (m != null) return m;
        RandomSource r = RandomSource.create(seed(l, s.x, s.z, who) ^ cycleMix(cycle));
        double mult = SIZE_MULT[Math.max(0, Math.min(2, s.size))];
        Map<Long, Integer> frag = new HashMap<>();
        Map<Long, List<Item>> tpl = new HashMap<>();

        double fragRate = dev.justin.infinium.InfiniumConfig.getDungeonChestFragmentRate();
        int baseFrag = (int) Math.round((FRAGMENTS_MIN_NORMAL + r.nextInt(Math.max(1, FRAGMENTS_MAX_NORMAL - FRAGMENTS_MIN_NORMAL + 1))) * fragRate);
        int nf = sround(baseFrag * mult, r);
        for (int i = 0; i < nf; i++) frag.merge(pick(s, FRAGMENT_DEPTH_BIAS, r), 1, Integer::sum);

        double wAstral = 3.0 * dev.justin.infinium.InfiniumConfig.getTemplateRateAstral();
        double wCyber = 3.0 * dev.justin.infinium.InfiniumConfig.getTemplateRateCybernetic();
        double wT1Total = wAstral + wCyber;
        double t1Mult = wT1Total > 0 ? (wT1Total / 6.0) : 0.0;
        int n1 = sround(TIER1_TEMPLATES_PER_NORMAL_DUNGEON * mult * t1Mult, r);
        for (int i = 0; i < n1; i++) {
            InfiniumStyle st = (r.nextDouble() * wT1Total < wAstral) ? InfiniumStyle.ASTRAL : InfiniumStyle.CYBERNETIC;
            tpl.computeIfAbsent(pick(s, TIER1_DEPTH_BIAS, r), x -> new ArrayList<>()).add(StyleTemplates.TEMPLATES.get(st));
        }

        double wSing = 1.0 * dev.justin.infinium.InfiniumConfig.getTemplateRateSingularity();
        double wPsycho = 1.0 * dev.justin.infinium.InfiniumConfig.getTemplateRateCyberpsycho();
        double wT2Total = wSing + wPsycho;
        double t2Mult = wT2Total > 0 ? (wT2Total / 2.0) : 0.0;
        int n2 = sround(RARE_TEMPLATES_PER_NORMAL_DUNGEON * mult * t2Mult, r);
        for (int i = 0; i < n2; i++) {
            InfiniumStyle st = (r.nextDouble() * wT2Total < wSing) ? InfiniumStyle.SINGULARITY : InfiniumStyle.CYBERPSYCHO;
            tpl.computeIfAbsent(pick(s, RARE_DEPTH_BIAS, r), x -> new ArrayList<>()).add(StyleTemplates.TEMPLATES.get(st));
        }
        m = new HashMap<>();
        for (var e : frag.entrySet()) m.computeIfAbsent(e.getKey(), x -> new ArrayList<>()).add(new ItemStack(Infinium.STRANGE_FRAGMENTS, e.getValue()));
        for (var e : tpl.entrySet()) for (Item it : e.getValue()) m.computeIfAbsent(e.getKey(), x -> new ArrayList<>()).add(new ItemStack(it));
        CACHE.put(k, m);
        return m;
    }
    /** Pick a container: floor by depth weight (only floors that have containers), then uniform within the floor. */
    static long pick(RoguelikeCompat.Site s, double bias, RandomSource r) {
        Map<Integer, List<Long>> byFloor = new TreeMap<>();
        for (int i = 0; i < s.chestPos.size(); i++) byFloor.computeIfAbsent(s.chestFloor.get(i), x -> new ArrayList<>()).add(s.chestPos.get(i));
        double tot = 0; for (int f : byFloor.keySet()) tot += weight(f, s.floorCount, bias);
        double x = r.nextDouble() * tot;
        for (var e : byFloor.entrySet()) { x -= weight(e.getKey(), s.floorCount, bias); if (x <= 0) return e.getValue().get(r.nextInt(e.getValue().size())); }
        List<Long> last = ((TreeMap<Integer, List<Long>>) byFloor).lastEntry().getValue();
        return last.get(r.nextInt(last.size()));
    }
    /** Not indexed / unknown container: independent roll with the same expected value per container. */
    static void fallback(ServerLevel l, RoguelikeCompat.Site s, BlockPos pos, UUID who, int cycle, List<ItemStack> drops) {
        int floors = s != null && s.floorCount > 0 ? s.floorCount : 11, floor = s != null ? RoguelikeCompat.floorAt(s, pos) : (floors + 1) / 2;
        double mult = SIZE_MULT[s != null ? Math.max(0, Math.min(2, s.size)) : 0], chests = floors * FALLBACK_CHESTS_PER_FLOOR;
        RandomSource r = RandomSource.create(seed(l, pos.getX(), pos.getZ(), who) ^ pos.asLong() * 0x2545F4914F6CDD1DL ^ cycleMix(cycle));
        double sf = 0, s1 = 0, s2 = 0;
        for (int f = 1; f <= floors; f++) { sf += weight(f, floors, FRAGMENT_DEPTH_BIAS); s1 += weight(f, floors, TIER1_DEPTH_BIAS); s2 += weight(f, floors, RARE_DEPTH_BIAS); }
        double fragRate = dev.justin.infinium.InfiniumConfig.getDungeonChestFragmentRate();
        double wAstral = 3.0 * dev.justin.infinium.InfiniumConfig.getTemplateRateAstral();
        double wCyber = 3.0 * dev.justin.infinium.InfiniumConfig.getTemplateRateCybernetic();
        double wT1Total = wAstral + wCyber;
        double t1Mult = wT1Total > 0 ? (wT1Total / 6.0) : 0.0;

        double wSing = 1.0 * dev.justin.infinium.InfiniumConfig.getTemplateRateSingularity();
        double wPsycho = 1.0 * dev.justin.infinium.InfiniumConfig.getTemplateRateCyberpsycho();
        double wT2Total = wSing + wPsycho;
        double t2Mult = wT2Total > 0 ? (wT2Total / 2.0) : 0.0;

        double ef = FRAGMENTS_PER_NORMAL_DUNGEON * mult * fragRate * weight(floor, floors, FRAGMENT_DEPTH_BIAS) / sf * floors / chests;
        double e1 = TIER1_TEMPLATES_PER_NORMAL_DUNGEON * mult * t1Mult * weight(floor, floors, TIER1_DEPTH_BIAS) / s1 * floors / chests;
        double e2 = RARE_TEMPLATES_PER_NORMAL_DUNGEON * mult * t2Mult * weight(floor, floors, RARE_DEPTH_BIAS) / s2 * floors / chests;
        if (ef > 0 && r.nextDouble() < ef / 1.5) drops.add(new ItemStack(Infinium.STRANGE_FRAGMENTS, 1 + r.nextInt(2)));   // mean stack 1.5
        if (e1 > 0 && r.nextDouble() < e1) {
            InfiniumStyle st = (r.nextDouble() * wT1Total < wAstral) ? InfiniumStyle.ASTRAL : InfiniumStyle.CYBERNETIC;
            drops.add(new ItemStack(StyleTemplates.TEMPLATES.get(st)));
        }
        if (e2 > 0 && r.nextDouble() < e2) {
            InfiniumStyle st = (r.nextDouble() * wT2Total < wSing) ? InfiniumStyle.SINGULARITY : InfiniumStyle.CYBERPSYCHO;
            drops.add(new ItemStack(StyleTemplates.TEMPLATES.get(st)));
        }
    }
    private DungeonLoot() {}
}
