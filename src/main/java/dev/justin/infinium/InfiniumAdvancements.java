package dev.justin.infinium;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.level.ServerPlayer;

/**
 * Centralized manager and trigger utility for Infinium advancements.
 */
public final class InfiniumAdvancements {
    private InfiniumAdvancements() {}

    /**
     * Awards the advancement by id name (e.g. "craft_crystal", "full_set", "infinity_max_charge").
     * Completes all remaining criteria for the advancement.
     *
     * @param player the player to award
     * @param name   the advancement path relative to the "infinium" namespace
     * @return true if newly awarded, false if already completed or advancement not found
     */
    public static boolean award(ServerPlayer player, String name) {
        if (player == null || player.level().getServer() == null) return false;
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(Infinium.id(name));
        if (holder == null) return false;

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) return false;

        boolean any = false;
        for (String criterion : progress.getRemainingCriteria()) {
            any |= player.getAdvancements().award(holder, criterion);
        }
        return any;
    }
}
