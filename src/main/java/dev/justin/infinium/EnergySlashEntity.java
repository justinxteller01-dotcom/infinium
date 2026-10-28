package dev.justin.infinium;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Piercing lightsaber energy wave or straight concentrated stab beam.
 * - Slashes: 15-node arched crescent ~3 blocks wide (u in [-1.4, +1.4]), dealing 50% sword damage (5.5).
 * - Stabs: Straight concentrated laser lance beam along aim, dealing 75% sword damage (8.25).
 * - 15 independent collision segments: block impacts only burn out colliding segments.
 * - Leaves color-coded sizzling plasma burn marks on blocks matching the sword's dye/style color.
 * - Surviving segments continue sailing forward until max range or all segments are depleted.
 */
public class EnergySlashEntity extends Projectile {
    private static final int MAX_AGE_TICKS = 20;
    public static final int SEGMENT_COUNT = 15;
    private static final double[] U_NODES = new double[SEGMENT_COUNT];

    static {
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            U_NODES[i] = -1.40 + i * 0.20; // 15 nodes spanning -1.40 to +1.40
        }
    }

    public static final EntityDataAccessor<Integer> ACTIVE_SEGMENTS = SynchedEntityData.defineId(EnergySlashEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(EnergySlashEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(EnergySlashEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> IS_STAB = SynchedEntityData.defineId(EnergySlashEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(EnergySlashEntity.class, EntityDataSerializers.INT);

    private int ageTicks = 0;
    private final Set<Integer> hitEntityIds = new HashSet<>();

    public EnergySlashEntity(EntityType<? extends EnergySlashEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ACTIVE_SEGMENTS, 0x7FFF); // All 15 bits set
        builder.define(ROLL, 0.0F);
        builder.define(COLOR, 0xB04CFF);
        builder.define(IS_STAB, false);
        builder.define(TIER, 1);
    }

    public int getActiveSegments() {
        return entityData.get(ACTIVE_SEGMENTS);
    }

    public void setActiveSegments(int mask) {
        entityData.set(ACTIVE_SEGMENTS, mask);
    }

    public float getRoll() {
        return entityData.get(ROLL);
    }

    public void setRoll(float roll) {
        entityData.set(ROLL, roll);
    }

    public int getColor() {
        return entityData.get(COLOR);
    }

    public void setColor(int color) {
        entityData.set(COLOR, color);
    }

    public boolean isStab() {
        return entityData.get(IS_STAB);
    }

    public void setStab(boolean stab) {
        entityData.set(IS_STAB, stab);
    }

    public int getTier() {
        return entityData.get(TIER);
    }

    public void setTier(int tier) {
        entityData.set(TIER, Mth.clamp(tier, 1, 3));
    }

    public int getAgeTicks() {
        return ageTicks;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
    }

    @Override
    public void tick() {
        super.tick();
        ageTicks++;
        if (ageTicks > MAX_AGE_TICKS) {
            discard();
            return;
        }

        int active = getActiveSegments();
        if (active == 0) {
            discard();
            return;
        }

        Vec3 pos = position();
        Vec3 motion = getDeltaMovement();
        Vec3 next = pos.add(motion);

        Vec3 fwd = motion.normalize();
        Vec3 upBase = Math.abs(fwd.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 r0 = fwd.cross(upBase).normalize();
        Vec3 u0 = r0.cross(fwd).normalize();

        float rollRad = (float) Math.toRadians(getRoll());
        float cosR = Mth.cos(rollRad);
        float sinR = Mth.sin(rollRad);
        Vec3 right = r0.scale(cosR).add(u0.scale(sinR));
        Vec3 up = r0.scale(-sinR).add(u0.scale(cosR));

        boolean hitBlockThisTick = false;
        Vec3 firstHitLoc = null;
        boolean stabMode = isStab();

        // Raycast each active segment individually against block colliders
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            if ((active & (1 << i)) == 0) continue;

            Vec3 offset;
            if (stabMode) {
                // Concentrated straight cylindrical laser lance: radial offset <= 0.12 blocks
                double angle = (i / (double) SEGMENT_COUNT) * Math.PI * 2.0;
                double r = (i % 3 == 0) ? 0.0 : 0.12;
                offset = right.scale(Math.cos(angle) * r).add(up.scale(Math.sin(angle) * r));
            } else {
                // 15-node arched crescent arc
                double u = U_NODES[i];
                offset = right.scale(u).subtract(fwd.scale(u * u * 0.22));
            }

            Vec3 segStart = pos.add(offset);
            Vec3 segEnd = next.add(offset);

            HitResult blockHit = level().clip(new ClipContext(segStart, segEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                // Deactivate only this specific sliced segment
                active &= ~(1 << i);
                Vec3 hitLoc = blockHit.getLocation();

                if (!hitBlockThisTick) {
                    hitBlockThisTick = true;
                    firstHitLoc = hitLoc;
                }

                // Spawn color-synchronized sizzling plasma burn mark on block surface
                spawnBlockBurnMark(hitLoc, getColor());
            }
        }

        // Play gentle block collision audio ONCE per tick (strictly debounced)
        if (hitBlockThisTick && firstHitLoc != null) {
            LivingEntity shooter = (getOwner() instanceof LivingEntity le) ? le : null;
            Holder<SoundEvent> s = ModSounds.sword(shooter, ModSounds.BLOCK_HIT, ModSounds.SWORD_SLASH_BLOCK_HIT);
            if (s != null) {
                float pitch = 0.95F + (random.nextFloat() * 0.15F);
                level().playSound(null, firstHitLoc.x, firstHitLoc.y, firstHitLoc.z,
                    s.value(), SoundSource.PLAYERS, 0.75F, pitch);
            }
        }

        // Update active segments; if all segments burned out, discard cleanly
        setActiveSegments(active);
        if (active == 0) {
            discard();
            return;
        }

        // Advance position
        setPos(next);

        // Spawn tight electric sparks around active segments (NO trailing dust)
        spawnSparks(pos, fwd, right, up, active, stabMode);

        // Check piercing entity collisions on server
        if (!level().isClientSide()) {
            checkEntityCollisions(next, fwd, right, up, active, stabMode);
        }
    }

    private void spawnBlockBurnMark(Vec3 hitLoc, int color) {
        DustParticleOptions coloredDust = new DustParticleOptions(color, 1.6F);

        if (level().isClientSide()) {
            level().addParticle(coloredDust, hitLoc.x, hitLoc.y, hitLoc.z, 0.0, 0.0, 0.0);
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, hitLoc.x, hitLoc.y, hitLoc.z, 0.0, 0.0, 0.0);
            level().addParticle(ParticleTypes.SMOKE, hitLoc.x, hitLoc.y, hitLoc.z, 0.0, 0.015, 0.0);
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, hitLoc.x, hitLoc.y, hitLoc.z, 0.0, 0.0, 0.0);
        } else if (level() instanceof ServerLevel sl) {
            sl.sendParticles(coloredDust, hitLoc.x, hitLoc.y, hitLoc.z, 3, 0.05, 0.05, 0.05, 0.0);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, hitLoc.x, hitLoc.y, hitLoc.z, 2, 0.04, 0.04, 0.04, 0.0);
            sl.sendParticles(ParticleTypes.SMOKE, hitLoc.x, hitLoc.y, hitLoc.z, 2, 0.04, 0.04, 0.04, 0.01);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, hitLoc.x, hitLoc.y, hitLoc.z, 2, 0.05, 0.05, 0.05, 0.02);
        }
    }

    private void spawnSparks(Vec3 pos, Vec3 fwd, Vec3 right, Vec3 up, int active, boolean stabMode) {
        if (!level().isClientSide()) return;
        int tier = getTier();
        SlashVisuals.Profile prof = SlashVisuals.ENABLED ? SlashVisuals.resolve(getOwner(), tier) : null;
        float sparkChance = prof != null ? prof.particleChance : (tier == 1 ? 0.12F : (tier == 2 ? 0.25F : 0.40F));
        double spd = prof != null ? prof.particleSpeed : 0.04;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            if ((active & (1 << i)) != 0 && random.nextFloat() < sparkChance) {
                Vec3 nodePos;
                if (stabMode) {
                    double angle = (i / (double) SEGMENT_COUNT) * Math.PI * 2.0;
                    double r = 0.10;
                    nodePos = pos.add(right.scale(Math.cos(angle) * r)).add(up.scale(Math.sin(angle) * r));
                } else {
                    double u = U_NODES[i];
                    nodePos = pos.add(right.scale(u)).subtract(fwd.scale(u * u * 0.22));
                }
                net.minecraft.core.particles.ParticleOptions type = ParticleTypes.ELECTRIC_SPARK;
                double vx = -fwd.x * spd, vy = -fwd.y * spd, vz = -fwd.z * spd;
                if (prof != null) {
                    type = random.nextFloat() < prof.secondaryChance ? prof.secondary : prof.primary;
                    if (prof.particleSpeed < 0) { // gravity warp: pull toward the slash centre
                        Vec3 in = pos.subtract(nodePos).scale(-prof.particleSpeed * 4.0);
                        vx = in.x; vy = in.y; vz = in.z;
                    }
                }
                level().addParticle(type, nodePos.x, nodePos.y, nodePos.z, vx, vy, vz);
            }
        }
    }

    private void checkEntityCollisions(Vec3 pos, Vec3 fwd, Vec3 right, Vec3 up, int active, boolean stabMode) {
        AABB box = getBoundingBox().inflate(0.6, 0.4, 0.6);
        List<LivingEntity> targets = level().getEntitiesOfClass(LivingEntity.class, box,
            e -> e.isAlive() && !e.isSpectator() && e != getOwner() && !hitEntityIds.contains(e.getId()));

        Entity owner = getOwner();
        float baseDmg = Infinium.getSwordBaseAttackDamageAttribute(); // 11.0F
        int tier = getTier();
        // Slash damage % of sword base: T1 30%, T2 40%, T3 50%.
        // Crit slash (stab beam): T1 35%, T2 55%, T3 75%.
        float pct;
        if (stabMode) {
            pct = switch (tier) {
                case 1 -> 0.35F;
                case 2 -> 0.55F;
                default -> 0.75F; // Tier 3
            };
        } else {
            pct = switch (tier) {
                case 1 -> 0.30F;
                case 2 -> 0.40F;
                default -> 0.50F; // Tier 3
            };
        }
        float damage = baseDmg * pct;

        for (LivingEntity target : targets) {
            Vec3 tPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

            // Proximity verification against surviving active segments
            boolean nearActive = false;
            for (int i = 0; i < SEGMENT_COUNT; i++) {
                if ((active & (1 << i)) == 0) continue;
                Vec3 nodePos;
                if (stabMode) {
                    nodePos = pos;
                } else {
                    double u = U_NODES[i];
                    nodePos = pos.add(right.scale(u)).subtract(fwd.scale(u * u * 0.22));
                }
                if (nodePos.distanceToSqr(tPos) <= (stabMode ? 1.5 * 1.5 : 1.25 * 1.25)) {
                    nearActive = true;
                    break;
                }
            }

            if (!nearActive) continue;

            hitEntityIds.add(target.getId());

            // Clear target invulnerability time so slash/beam pierces cleanly
            target.invulnerableTime = 0;
            DamageSource ds = (owner instanceof Player p) ? damageSources().playerAttack(p) : damageSources().thrown(this, owner);
            target.hurtServer((ServerLevel) level(), ds, damage);

            // Apply 8-second Bleed (Burn for a Cybernetic / Cyber Psycho owner)
            target.addEffect(new MobEffectInstance(BleedingEffect.forOwner(owner), 8 * 20 + 1, 0), owner);

            // Compress target i-frames based on cyber-tier attack speed bonus
            if (owner instanceof LivingEntity attacker) {
                SetLook look = SetLook.of(attacker);
                if (look == SetLook.CYBERNETIC || look == SetLook.CYBERPSYCHO || look == SetLook.INFINITY) {
                    double bonus = SetBonuses.attack(attacker, look);
                    if (bonus > 0) {
                        float speedRatio = 1.0f + (float) bonus;
                        int comp = Math.max(1, Math.round(10.0f / speedRatio));
                        target.invulnerableTime = 10 + comp;
                        target.hurtDuration = comp;
                        target.hurtTime = Math.min(target.hurtTime, comp);
                    }
                }
            }

            // Impact audio and particles
            LivingEntity shooter = (owner instanceof LivingEntity le) ? le : null;
            Holder<SoundEvent> s = ModSounds.sword(shooter, ModSounds.ENTITY_HIT, Holder.direct(SoundEvents.PLAYER_ATTACK_CRIT));
            if (s != null) {
                level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    s.value(), SoundSource.PLAYERS, 1.0F, stabMode ? 1.4F : 1.25F);
            }
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 10, 0.25, 0.25, 0.25, 0.18);
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.5), target.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        // Individual segments handle block collisions per-node in tick()
    }
}
