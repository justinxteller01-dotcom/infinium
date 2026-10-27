package dev.justin.infinium.ability;

import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * One activatable Infinium ability (M1 core; warp = M2, X-ray pulse = M3 plug in here).
 *
 * @param id            unique id, e.g. infinium:warp. Also names the key mapping "key.infinium.ability.&lt;path&gt;".
 * @param icon          HUD icon texture (square PNG, {@link #ICON_TEX_SIZE} px), e.g. infinium:textures/gui/ability/warp.png
 * @param cooldownTicks cooldown for this player in ticks; read the worn tier here for per-tier cooldowns
 *                      (see {@link #perTier}). Must work on both sides (client uses it only for display hints).
 * @param owned         does the player have it right now (full set or piece check). Must work on both sides,
 *                      it only reads worn equipment, which is synced.
 * @param action        server-side effect. Return true if it fired (cooldown starts), false if it was not
 *                      applicable right now (e.g. warp on the ground) - no cooldown is spent then.
 */
public record Ability(Identifier id, Identifier icon, ToIntFunction<Player> cooldownTicks,
                      Predicate<Player> owned, Predicate<ServerPlayer> action) {

    public static final int ICON_TEX_SIZE = 32;

    public String keyName() { return "key.infinium.ability." + id.getPath(); }
    public String nameKey() { return "ability.infinium." + id.getPath(); }

    /** Enabled toggle hook (M5 wires it to Infinium Settings / matrix). Both sides. */
    public boolean enabled(Player p) { return Abilities.enabledResolver().test(p, this); }

    /**
     * Per-tier cooldown helper. Index 0 = tier 1 (Astral/Cybernetic), 1 = tier 2 (Singularity/Cyberpsycho),
     * 2 = tier 3 (Infinity); values in SECONDS. Uses {@code tierOf} so callers can choose full-set or piece tier.
     * Tier 0 (not owned) falls back to the tier-1 value.
     */
    public static ToIntFunction<Player> perTier(ToIntFunction<Player> tierOf, double t1Sec, double t2Sec, double t3Sec) {
        return p -> {
            int t = tierOf.applyAsInt(p);
            double s = t >= 3 ? t3Sec : t == 2 ? t2Sec : t1Sec;
            return (int) Math.round(s * 20);
        };
    }
}
