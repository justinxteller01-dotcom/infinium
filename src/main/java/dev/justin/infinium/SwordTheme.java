package dev.justin.infinium;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

/**
 * Common helper for resolving per-player sword sound themes based on worn armor set.
 * Theme values correspond to SetLook ordinals:
 * 0: Astral, 1: Cybernetic, 2: Singularity, 3: Cyberpsycho, 4: Infinity.
 */
public final class SwordTheme {
    private static final Map<UUID, byte[]> CHOICES = new ConcurrentHashMap<>();

    private SwordTheme() {}

    /**
     * Checks if a theme choice is permitted for the worn armor set look.
     */
    public static boolean allowed(SetLook worn, int choice) {
        if (worn == null) return false;
        return switch (worn) {
            case ASTRAL -> choice == 0;
            case CYBERNETIC -> choice == 1;
            case SINGULARITY -> choice == 0 || choice == 2;
            case CYBERPSYCHO -> choice == 1 || choice == 3;
            case INFINITY -> choice >= 0 && choice <= 4;
        };
    }

    /**
     * Resolves the theme ordinal to use. Returns the choice if allowed, otherwise falls back to the worn set's own ordinal.
     */
    public static int resolve(SetLook worn, int choice) {
        if (worn == null) return 0;
        return allowed(worn, choice) ? choice : worn.ordinal();
    }

    /**
     * Returns the resolved sound theme ordinal for the given player and worn set look.
     * Returns the set's own ordinal if player has no stored choice or choice is invalid.
     */
    public static int choiceFor(Player p, SetLook worn) {
        if (worn == null) return 0;
        if (p == null) return worn.ordinal();
        byte[] arr = CHOICES.get(p.getUUID());
        if (arr == null || worn.ordinal() >= arr.length) return worn.ordinal();
        return resolve(worn, arr[worn.ordinal()]);
    }

    /**
     * Stores the per-player choice array (byte[5], index = worn set ordinal).
     */
    public static void set(UUID uuid, byte[] choices) {
        if (uuid == null) return;
        if (choices == null) {
            CHOICES.remove(uuid);
        } else {
            CHOICES.put(uuid, choices.clone());
        }
    }

    /**
     * Removes stored choices for a disconnected player.
     */
    public static void remove(UUID uuid) {
        if (uuid != null) {
            CHOICES.remove(uuid);
        }
    }
}
