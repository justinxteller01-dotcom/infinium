package dev.justin.infinium;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Singularity, Cyberpsycho, and Infinity rocket-free elytra flight.
 * While flying, holding the Flight Thrust key (default Left Ctrl) gradually ramps speed on an S-curve
 * to vanilla max rocket elytra speed (same blend as FireworkRocketEntity) over the ramp time.
 * When thrust is released, ramp decays and vanilla elytra glide physics takes over.
 * Astral and Cybernetic behave as standard vanilla elytras.
 */
public final class InfinityFlight {
    public static final double ROCKET_CAP = 1.50; // max vanilla elytra rocket speed in blocks/tick
    public static final int MAX_RAMP_TICKS = 50; // default 2.5 seconds at 20 ticks/sec
    public static int maxRampTicks() {
        return Math.max(10, (int) (InfiniumConfig.getFlightRampSeconds() * 20.0));
    }
    private static final Map<UUID, Integer> RAMP_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> THRUST_HELD = new ConcurrentHashMap<>();

    public static void setThrustHeld(UUID uuid, boolean held) {
        if (uuid == null) return;
        if (held) THRUST_HELD.put(uuid, true);
        else THRUST_HELD.remove(uuid);
    }

    public static boolean grantsThrustFlight(ItemStack chest) {
        if (chest == null || !Infinium.isChestplate(chest)) return false;
        if (!chest.has(net.minecraft.core.component.DataComponents.GLIDER)) return false;
        if (Infinium.isInfinity(chest)) return true;
        InfiniumStyle st = InfiniumStyle.get(chest);
        return st == InfiniumStyle.SINGULARITY || st == InfiniumStyle.CYBERPSYCHO;
    }

    public static boolean isThrustFlightActive(Player player) {
        if (player == null || !player.isFallFlying()) return false;
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        return grantsThrustFlight(chest);
    }

    public static boolean isInfinityFlightActive(Player player) {
        return isThrustFlightActive(player);
    }

    public static boolean isHoldingThrust(Player player) {
        if (player == null) return false;
        if (player.level().isClientSide()) {
            return dev.justin.infinium.client.InfinityFlightClient.isHoldingThrust(player);
        } else if (player instanceof ServerPlayer sp) {
            Boolean custom = THRUST_HELD.get(sp.getUUID());
            return custom != null && custom;
        }
        return false;
    }

    public static boolean isHoldingForward(Player player) {
        return isHoldingThrust(player);
    }

    public static float getRampProgress(Player player) {
        if (player == null) return 0.0F;
        int ramp = RAMP_TICKS.getOrDefault(player.getUUID(), 0);
        return (float) ramp / (float) maxRampTicks();
    }

    public static void reset(UUID uuid) {
        if (uuid != null) {
            RAMP_TICKS.remove(uuid);
            THRUST_HELD.remove(uuid);
        }
    }

    public static Vec3 handleFlightThrust(Player player, Vec3 motion) {
        if (!isThrustFlightActive(player)) {
            reset(player.getUUID());
            return motion;
        }

        UUID uuid = player.getUUID();
        int ramp = RAMP_TICKS.getOrDefault(uuid, 0);
        int maxTicks = maxRampTicks();

        boolean holdingThrust = isHoldingThrust(player);
        if (holdingThrust) {
            ramp = Math.min(maxTicks, ramp + 1);
        } else {
            ramp = Math.max(0, ramp - 2);
        }
        RAMP_TICKS.put(uuid, ramp);

        if (ramp <= 0) {
            return motion;
        }

        // Smoothstep curve: c = p^2 * (3 - 2p)
        double p = (double) ramp / (double) maxTicks;
        double curve = p * p * (3.0 - 2.0 * p);

        Vec3 look = player.getLookAngle();

        // Vanilla FireworkRocketEntity boost: delta = look * 1.5 - motion, boost = delta * 0.5 + look * 0.1
        // Scaled by ramp progress so at full ramp it matches holding rockets continuously and never exceeds it
        Vec3 delta = look.scale(ROCKET_CAP).subtract(motion);
        Vec3 boost = delta.scale(0.5 * curve).add(look.scale(0.1 * curve));
        Vec3 boosted = motion.add(boost);

        // Merge fix (G3): no extra hard clamp. The blend above is exactly vanilla's per-tick rocket boost, so at full
        // ramp the glide settles at vanilla's continuous rocket-spam speed (~1.67 b/t after drag) and can never exceed it.
        // The old 1.5 length clamp made it slower than rockets and braked dives/gravity speed while thrusting or decaying.
        return boosted;
    }

    private InfinityFlight() {}
}
