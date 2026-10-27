package dev.justin.infinium.loot;

import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * One seed function so a chest hint and the later drop use the same roll.
 * Natural: worldSeed ^ position ^ player UUID ^ cycle.
 * Dungeon: worldSeed ^ dungeonId ^ player UUID ^ cycle.
 * R5c DungeonLoot / StyleLoot should call these. If they keep a different mix, hints will not match cycle 0.
 */
public final class ShareSeed {
    public static long natural(long worldSeed, BlockPos pos, UUID player, int cycle) {
        long s = worldSeed ^ pos.asLong() ^ player.getMostSignificantBits();
        s = s * 0x9E3779B97F4A7C15L ^ player.getLeastSignificantBits();
        s ^= (long) cycle * 0xBF58476D1CE4E5B9L;
        return s;
    }

    public static long dungeon(long worldSeed, long dungeonId, UUID player, int cycle) {
        long s = worldSeed ^ dungeonId ^ player.getMostSignificantBits();
        s = s * 0x9E3779B97F4A7C15L ^ player.getLeastSignificantBits();
        s ^= (long) cycle * 0x94D049BB133111EBL;
        return s;
    }

    private ShareSeed() {}
}
