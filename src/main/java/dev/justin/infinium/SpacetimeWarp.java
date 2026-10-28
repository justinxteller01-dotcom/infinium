package dev.justin.infinium;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;


/**
 * Spacetime Warp (Batch 01): Directional, non-phasing blink/teleport tied to leggings pieces.
 * Player-facing name: "Quantum Tunnel" (batch1). Tunables: DISTANCE / COOLDOWN_SEC / LAUNCH_SPEED below.
 * - Astral Leggings: 8 blocks, 8 s.  Singularity: 12 blocks, 6 s.  Infinity: 16 blocks, 4 s.
 * - Ends with a forward launch along the warp direction (small/medium/large) + placeholder audio cue.
 * - (Cybernetic and CyberPsycho leggings are explicitly excluded).
 * - M2: an ability in the M1 system (id infinium:warp, own key, unbound by default). Mid-air only. Cooldown
 *   (see COOLDOWN_SEC; server-authoritative, see ability.Abilities). The player comes
 *   out of the warp sprinting (server + client).
 * - Anti-Phasing: Sweeps the player box along the look vector (blocks only, passes through entities). Obstacles up to
 *   1.9 above the current feet Y are stepped onto (measured from each step); higher ones or no 1.8 headroom stop the warp.
 */
public final class SpacetimeWarp {
    public static final net.minecraft.resources.Identifier ID = Infinium.id("warp");
    public static dev.justin.infinium.ability.Ability ABILITY;

    /** Called from Infinium.onInitialize (common init, before client key registration). */
    public static void register() {
        ABILITY = dev.justin.infinium.ability.Abilities.register(new dev.justin.infinium.ability.Ability(
            ID, Infinium.id("textures/gui/ability/warp.png"),
            SpacetimeWarp::cooldownTicks,
            p -> ownsWarp(p),
            SpacetimeWarp::tryWarp));
    }

    /** Owned = wearing warp-capable leggings (Astral / Singularity / Infinity), not broken. Same rule as getWarpDistance. */
    public static boolean ownsWarp(Player p) { return getWarpDistance(p) > 0.0; }

    // ---- Quantum Tunnel tunables (batch1, 2026-10-28). Index 0 = tier 1 (Astral leggings), 1 = tier 2
    // (Singularity leggings), 2 = tier 3 (Infinity leggings). ----
    /** Max tunnel distance in blocks (was 4/6/8). */
    public static final double[] DISTANCE = {8.0, 12.0, 16.0};
    /** Cooldown in seconds (was 12/9/6). */
    public static final double[] COOLDOWN_SEC = {8.0, 6.0, 4.0};
    /** End-of-tunnel forward launch speed in blocks/tick along the warp direction (small / medium / large). */
    public static final double[] LAUNCH_SPEED = {0.55, 0.85, 1.20};
    /** Extra upward kick added to the launch so a flat tunnel doesn't dump the player straight down (blocks/tick). */
    public static final double LAUNCH_LIFT = 0.10;
    /** PLACEHOLDER cue pitch per tier (vanilla enderman teleport until the real clip exists). */
    public static final float[] CUE_PITCH = {1.25F, 1.0F, 0.8F};

    /** 1..3 from the worn leggings, 0 if they can't warp. */
    public static int tier(Player p) {
        if (p == null) return 0;
        ItemStack legs = p.getItemBySlot(EquipmentSlot.LEGS);
        if (Infinium.isInfinity(legs)) return 3;
        InfiniumStyle st = InfiniumStyle.get(legs);
        if (st == InfiniumStyle.SINGULARITY) return 2;
        if (st == InfiniumStyle.ASTRAL) return 1;
        return 0;
    }

    /** Cooldown by the worn leggings: Astral 8 s, Singularity 6 s, Infinity 4 s. */
    public static int cooldownTicks(Player p) {
        return (int) Math.round(COOLDOWN_SEC[Math.max(1, tier(p)) - 1] * 20);
    }

    /**
     * M2 mid-air rule: any time airborne. Not on the ground, not in water or lava, not on a ladder/vine,
     * not riding. Elytra gliding COUNTS (Justin: "only work when the player is mid air").
     */
    public static boolean isMidAir(Player p) {
        return !p.onGround() && !p.isInWater() && !p.isInLava() && !p.onClimbable() && !p.isPassenger();
    }

    public static double getWarpDistance(Player player) {
        if (player == null) return 0.0;
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        if (legs == null || !Infinium.isLeggings(legs) || InfiniumDurability.disabled(legs)) return 0.0;
        int fam = Perk.family(legs);
        if (!Perks.on(player, Perk.piece(fam, EquipmentSlot.LEGS))) return 0.0;

        int t = tier(player);
        return t == 0 ? 0.0 : DISTANCE[t - 1];
    }

    public static boolean canWarp(Player player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return false;
        if (!isMidAir(player)) return false;
        return getWarpDistance(player) > 0.0;   // cooldown is handled by the ability system
    }

    public static boolean tryWarp(ServerPlayer player) {
        if (!canWarp(player)) return false;
        double maxDist = getWarpDistance(player);
        if (maxDist <= 0.0) return false;

        ServerLevel level = (ServerLevel) player.level();
        Vec3 origin = player.position();
        Vec3 look = player.getLookAngle();
        if (look.lengthSqr() < 1e-6) look = new Vec3(0, 0, 1);
        Vec3 dir = look.normalize();

        // G4b step-up sweep (Justin 2026-10-27): walk the warp line in 0.1-block samples with the player's box.
        // Blocks only (entities ignored, so the warp passes through mobs). A blocked sample whose obstacle top is
        // 0..1.9 above the current reference feet Y steps up onto it (reference resets each step, so stairs and
        // hills climb step by step); 1.91+ or no headroom for the full hitbox stops just before the face.
        double halfW = player.getBbWidth() / 2.0;
        double bbHeight = player.getBbHeight();
        double step = 0.1;
        Vec3 delta = dir.scale(step);
        Vec3 cur = origin;
        Vec3 safePos = origin;
        double safeDist = 0.0;
        for (double d = step; d <= maxDist + 1e-9; d += step) {
            // G4b rework: full look vector, pitch included (no clamp). Vertical part first:
            double ny = cur.y + delta.y;
            if (!clear(level, player, box(new Vec3(cur.x, ny, cur.z), halfW, bbHeight))) {
                if (delta.y > 0) break;   // ceiling / overhang going up: stop at last clear spot
                ny = cur.y;               // floor going down: slide along it
            }
            // Horizontal part, with step-up (only blocks hit horizontally step up):
            Vec3 cand = new Vec3(cur.x + delta.x, ny, cur.z + delta.z);
            if (!clear(level, player, box(cand, halfW, bbHeight))) {
                // Obstacle top relative to the current reference feet Y (cand.y), from block collision shapes.
                AABB probe = new AABB(cand.x - halfW, cand.y, cand.z - halfW, cand.x + halfW, cand.y + STEP_MAX, cand.z + halfW);
                double top = Double.NEGATIVE_INFINITY;
                for (net.minecraft.world.phys.shapes.VoxelShape vs : level.getBlockCollisions(player, probe)) {
                    for (AABB bb : vs.toAabbs()) if (bb.intersects(probe)) top = Math.max(top, bb.maxY);
                }
                double rise = top - cand.y;
                if (top == Double.NEGATIVE_INFINITY || rise > STEP_MAX) break;      // 1.91+ (or unknown): wall
                Vec3 up = new Vec3(cand.x, top + 1.0E-3, cand.z);
                if (!clear(level, player, box(up, halfW, bbHeight))) break;         // no headroom (1-tall gap, ceiling)
                cand = up;                                                           // step up; new reference Y
            }
            cur = cand;
            safePos = cand;
            safeDist = d;
        }

        // If no safe distance can be traveled, cancel warp cleanly without clipping
        if (safeDist < 0.25 || !clear(level, player, box(safePos, halfW, bbHeight))) {
            return false;
        }

        // Resolve style/tier for custom FX
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        int fam = Infinium.isInfinity(legs) ? 4 : (InfiniumStyle.get(legs) == InfiniumStyle.SINGULARITY ? 2 : 0);

        // 3. Audio at departure point
        level.playSound(null, origin.x, origin.y, origin.z, ModSounds.warpDeparture().value(), SoundSource.PLAYERS, 0.90F, 1.0F);

        // 4. Particles at departure point and along trajectory
        spawnWarpParticles(level, origin, safePos, fam);

        // 5. Execute teleportation
        player.teleportTo(safePos.x, safePos.y, safePos.z);
        player.fallDistance = 0.0F;
        AirJumps.onWarp(player);    // G4b: Infinity refills its air jumps
        player.setSprinting(true);   // M2: come out of the warp sprinting (client mirrors it on the started packet)

        // batch1 B3: launch forward along the warp direction, tiered momentum (tunables above).
        int t = Math.max(1, tier(player));
        Vec3 launch = dir.scale(LAUNCH_SPEED[t - 1]).add(0, LAUNCH_LIFT, 0);
        player.setDeltaMovement(launch);
        player.hurtMarked = true;    // forces the velocity packet so the client actually gets the push

        // batch1 B4: PLACEHOLDER warp cue (vanilla enderman teleport, pitched by tier). Replace with a real clip later.
        level.playSound(null, safePos.x, safePos.y, safePos.z, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, CUE_PITCH[t - 1]);

        // 6. Audio at arrival point
        level.playSound(null, safePos.x, safePos.y, safePos.z, ModSounds.warpArrival().value(), SoundSource.PLAYERS, 0.95F, 1.0F);

        // 7. Arrival impact particles
        spawnArrivalParticles(level, safePos, fam);

        return true;
    }

    /** Max auto step-up height (Justin: 0 to 1.9 steps up, 1.91+ is a wall). */
    private static final double STEP_MAX = 1.9;

    private static AABB box(Vec3 feet, double halfW, double h) {
        return new AABB(feet.x - halfW, feet.y, feet.z - halfW, feet.x + halfW, feet.y + h, feet.z + halfW);
    }

    /** Blocks and world border only; entities are ignored. */
    private static boolean clear(ServerLevel level, Player p, AABB b) {
        return level.noBlockCollision(p, b) && level.noBorderCollision(p, b);
    }

    private static void spawnWarpParticles(ServerLevel level, Vec3 from, Vec3 to, int fam) {
        // Departure burst
        int color = (fam == 4) ? 0xFF00AA : (fam == 2 ? 0x8A2BE2 : 0xFF00AA);
        DustParticleOptions dust = new DustParticleOptions(color, 1.2F);
        level.sendParticles(dust, from.x, from.y + 0.9, from.z, 20, 0.35, 0.5, 0.35, 0.05);
        level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 0.9, from.z, 15, 0.25, 0.4, 0.25, 0.3);

        // Directional trail line from origin to destination
        Vec3 diff = to.subtract(from);
        double dist = diff.length();
        int steps = Math.max(3, (int) (dist * 4.0));
        for (int i = 1; i < steps; i++) {
            double frac = (double) i / (double) steps;
            Vec3 p = from.add(diff.scale(frac));
            level.sendParticles(dust, p.x, p.y + 0.9, p.z, 1, 0.05, 0.05, 0.05, 0.0);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y + 0.9, p.z, 1, 0.02, 0.02, 0.02, 0.01);
            }
        }
    }

    private static void spawnArrivalParticles(ServerLevel level, Vec3 pos, int fam) {
        int color = (fam == 4) ? 0xFF00AA : (fam == 2 ? 0xD24CFF : 0xFF00AA);
        DustParticleOptions dust = new DustParticleOptions(color, 1.3F);
        level.sendParticles(dust, pos.x, pos.y + 0.9, pos.z, 25, 0.4, 0.6, 0.4, 0.06);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.9, pos.z, 10, 0.3, 0.5, 0.3, 0.04);
        if (fam == 4) {
            level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFF), pos.x, pos.y + 1.0, pos.z, 1, 0, 0, 0, 0);
        }
    }

    private SpacetimeWarp() {}
}
