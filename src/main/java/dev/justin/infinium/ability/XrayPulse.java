package dev.justin.infinium.ability;

import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumDurability;
import dev.justin.infinium.InfiniumStyle;
import dev.justin.infinium.ModSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

/**
 * M3 X-ray PULSE ability (replaces the old continuous client X-ray). One server-side scan of a sphere around the
 * player when the key is pressed; results go only to that player as an {@link XrayPulsePayload}, and the client
 * draws fading holograms. Tier = the worn Infinium HELMET's set (any set, per Justin's part3 answer 1):
 * T1 Astral/Cybernetic r10 / 40 s, T2 Singularity/Cyberpsycho r20 / 30 s, T3 Infinity r30 / 20 s.
 * Runs on the server thread (no background thread): at most ~113k block-state reads every 20 s, sections that are
 * all air are skipped.
 */
public final class XrayPulse {
    public static final Identifier ID = Infinium.id("xray_pulse");
    public static Ability ABILITY;

    // kinds sent to the client
    public static final byte ORE = 0, DEBRIS = 1, CHEST = 2, HOSTILE = 3, PLAYER = 4;
    public static final int MAX_BLOCKS = 400, MAX_ENTITIES = 48;

    // hoisted tag keys (old code allocated c:ores per block per scan)
    private static final TagKey<Block> C_ORES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));
    // 26.2 BlockTags only exposes GOLD/IRON/COPPER_ORES as fields; the other vanilla block tags exist in data
    private static TagKey<Block> mc(String path) { return TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace(path)); }
    private static final TagKey<Block> COAL_ORES = mc("coal_ores"), REDSTONE_ORES = mc("redstone_ores"), LAPIS_ORES = mc("lapis_ores"),
        DIAMOND_ORES = mc("diamond_ores"), EMERALD_ORES = mc("emerald_ores");
    @SuppressWarnings("unchecked")
    private static final TagKey<Block>[] VANILLA_ORES = new TagKey[]{COAL_ORES, BlockTags.IRON_ORES, BlockTags.COPPER_ORES,
        BlockTags.GOLD_ORES, REDSTONE_ORES, LAPIS_ORES, DIAMOND_ORES, EMERALD_ORES};

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(XrayPulsePayload.TYPE, XrayPulsePayload.CODEC);
        ABILITY = Abilities.register(new Ability(ID, Infinium.id("textures/gui/ability/xray_pulse.png"),
            p -> switch (tier(p)) { case 3 -> 20 * 20; case 2 -> 30 * 20; default -> 40 * 20; },
            p -> tier(p) > 0,
            XrayPulse::pulse));
    }

    /** Helmet tier: 1 Astral/Cybernetic, 2 Singularity/Cyberpsycho, 3 Infinity, 0 no usable Infinium helmet. */
    public static int tier(Player p) {
        ItemStack h = p.getItemBySlot(EquipmentSlot.HEAD);
        if (h.isEmpty() || !Infinium.isHelmet(h) || InfiniumDurability.disabled(h)) return 0;
        if (Infinium.isInfinity(h)) return 3;
        InfiniumStyle st = InfiniumStyle.get(h);
        if (st == InfiniumStyle.SINGULARITY || st == InfiniumStyle.CYBERPSYCHO) return 2;
        return 1;   // Astral, Cybernetic (and an unstyled base helmet)
    }

    public static int radius(Player p) { return switch (tier(p)) { case 3 -> 30; case 2 -> 20; case 1 -> 10; default -> 0; }; }

    static boolean pulse(ServerPlayer sp) {
        int r = radius(sp);
        if (r <= 0) return false;
        ServerLevel level = (ServerLevel) sp.level();
        BlockPos c = sp.blockPosition();
        long r2 = (long) r * r;
        List<XrayPulsePayload.Hit> blocks = new ArrayList<>();
        int minY = Math.max(level.getMinY(), c.getY() - r), maxY = Math.min(level.getMaxY(), c.getY() + r);
        for (int cx = (c.getX() - r) >> 4; cx <= (c.getX() + r) >> 4; cx++) {
            for (int cz = (c.getZ() - r) >> 4; cz <= (c.getZ() + r) >> 4; cz++) {
                if (!(level.getChunkSource().getChunk(cx, cz, false) instanceof LevelChunk chunk)) continue;
                LevelChunkSection[] secs = chunk.getSections();
                for (int si = 0; si < secs.length; si++) {
                    LevelChunkSection sec = secs[si];
                    if (sec == null || sec.hasOnlyAir()) continue;
                    int by0 = chunk.getSectionYFromSectionIndex(si) << 4;
                    if (by0 + 15 < minY || by0 > maxY) continue;
                    for (int y = 0; y < 16; y++) {
                        int wy = by0 + y; long dy = wy - c.getY();
                        if (wy < minY || wy > maxY) continue;
                        for (int z = 0; z < 16; z++) {
                            int wz = (cz << 4) + z; long dz = wz - c.getZ();
                            for (int x = 0; x < 16; x++) {
                                int wx = (cx << 4) + x; long dx = wx - c.getX();
                                if (dx * dx + dy * dy + dz * dz > r2) continue;
                                BlockState st = sec.getBlockState(x, y, z);
                                int col = color(st);
                                if (col != 0) blocks.add(new XrayPulsePayload.Hit(BlockPos.asLong(wx, wy, wz), kind(st), col));
                            }
                        }
                    }
                }
            }
        }
        if (blocks.size() > MAX_BLOCKS) {   // keep the nearest
            blocks.sort(Comparator.comparingDouble(h -> BlockPos.of(h.pos()).distSqr(c)));
            blocks = new ArrayList<>(blocks.subList(0, MAX_BLOCKS));
        }
        List<XrayPulsePayload.Mob> mobs = new ArrayList<>();
        AABB box = sp.getBoundingBox().inflate(r);
        for (Entity e : level.getEntities(sp, box, e -> e.isAlive() && (e instanceof Enemy || (e instanceof Player pl && !pl.isSpectator())))) {
            if (e.distanceToSqr(sp) > r2) continue;
            mobs.add(new XrayPulsePayload.Mob(e.getId(), e instanceof Player ? PLAYER : HOSTILE));
            if (mobs.size() >= MAX_ENTITIES) break;
        }
        ServerPlayNetworking.send(sp, new XrayPulsePayload(r, blocks, mobs));
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.8F);
        return true;
    }

    static byte kind(BlockState s) {
        if (s.is(Blocks.ANCIENT_DEBRIS)) return DEBRIS;
        return isChest(s) ? CHEST : ORE;
    }

    /** ANY chest (Justin part3 answer 2): chest, trapped chest, barrel, ender chest, shulker boxes, Lootr containers. */
    public static boolean isChest(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof EnderChestBlock || b instanceof ShulkerBoxBlock) return true;
        Identifier id = BuiltInRegistries.BLOCK.getKey(b);   // Lootr by registry id, no dependency
        if (!"lootr".equals(id.getNamespace())) return false;
        String p = id.getPath();
        return p.contains("chest") || p.contains("barrel") || p.contains("shulker");
    }

    public static boolean isOre(BlockState s) {
        if (s.is(C_ORES) || s.is(Blocks.ANCIENT_DEBRIS) || s.is(Blocks.NETHER_QUARTZ_ORE) || s.is(Blocks.NETHER_GOLD_ORE)
            || s.is(Blocks.BUDDING_AMETHYST)) return true;
        for (TagKey<Block> t : VANILLA_ORES) if (s.is(t)) return true;
        return false;
    }

    /** ARGB hologram colour, 0 = not a target. Ore colours kept from the old X-ray. */
    public static int color(BlockState s) {
        if (s.isAir()) return 0;
        // X-Ray Pulse is an EXCEPTION to the purple/pink/black colour rule: each target glows the colour of what it is.
        if (isChest(s)) return 0xFFFF00FF;   // all containers (chest/barrel/ender/shulker/Lootr): magenta, unique vs every ore
        if (!isOre(s)) return 0;
        if (s.is(Blocks.ANCIENT_DEBRIS)) return 0xFF6B3A1E;                    // brown
        if (s.is(DIAMOND_ORES)) return 0xFF4DE8FF;                             // light blue
        if (s.is(EMERALD_ORES)) return 0xFF00D040;                             // green
        if (s.is(BlockTags.GOLD_ORES) || s.is(Blocks.NETHER_GOLD_ORE)) return 0xFFFFD700; // gold
        if (s.is(BlockTags.IRON_ORES)) return 0xFFD8B48C;                      // iron tan
        if (s.is(BlockTags.COPPER_ORES)) return 0xFFE0702A;                    // copper orange
        if (s.is(REDSTONE_ORES)) return 0xFFE00000;                            // red
        if (s.is(LAPIS_ORES)) return 0xFF1A3CC8;                               // deep blue
        if (s.is(COAL_ORES)) return 0xFF242424;                                // near-black (visible)
        if (s.is(Blocks.NETHER_QUARTZ_ORE)) return 0xFFFFFFFF;                 // white
        if (s.is(Blocks.BUDDING_AMETHYST)) return 0xFF9A5CFF;                  // amethyst purple
        return 0xFFA0A0A0;   // any other c:ores (modded): neutral silver
    }

    private XrayPulse() {}
}
