package dev.justin.infinium;

import net.minecraft.world.entity.LivingEntity;

/**
 * Server and client authoritative helper for resolving Infinium armor set tiers.
 * FULL set worn (all 4 pieces of the same set):
 * Tier 1 = Astral or Cybernetic
 * Tier 2 = Singularity or Cyberpsycho
 * Tier 3 = Infinity
 * Else 0. Highest tier wins.
 */
public final class InfiniumArmorTier {
    private InfiniumArmorTier() {}

    /**
     * Resolves the current worn Infinium armor tier for the entity.
     * Evaluates live equipment dynamically; putting on a full set enables tier >= 1 immediately.
     */
    public static int getTier(LivingEntity player) {
        SetLook look = SetLook.of(player);
        if (look == null) return 0;
        return switch (look) {
            case INFINITY -> 3;
            case SINGULARITY, CYBERPSYCHO -> 2;
            case ASTRAL, CYBERNETIC -> 1;
        };
    }
}
