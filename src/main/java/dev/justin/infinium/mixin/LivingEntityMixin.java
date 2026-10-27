package dev.justin.infinium.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.justin.infinium.Infinium;
import dev.justin.infinium.InfiniumDurability;
import dev.justin.infinium.Perk;
import dev.justin.infinium.Perks;
import dev.justin.infinium.PerkValues;
import dev.justin.infinium.SetBonuses;
import dev.justin.infinium.SetLook;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private java.util.Map<Holder<net.minecraft.world.effect.MobEffect>, net.minecraft.world.effect.MobEffectInstance> activeEffects;
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private static net.minecraft.network.syncher.EntityDataAccessor<java.util.List<net.minecraft.core.particles.ParticleOptions>> DATA_EFFECT_PARTICLES;

    private LivingEntity infinium$self() { return (LivingEntity) (Object) this; }
    private static Perk infinium$piece(LivingEntity e, EquipmentSlot s) { return Perk.piece(Perk.family(e.getItemBySlot(s)), s); }
    /** Pre-armor amount from the matching getDamageAfterArmorAbsorb call. -1 = none captured. */
    @Unique private float infinium$incoming = -1F;
    @Unique private DamageSource infinium$source;

    // Suppress Speed, Haste, Strength, and Regeneration potion swirl particles on all players
    @Inject(method = "updateSynchronizedMobEffectParticles", at = @At("TAIL"))
    private void infinium$suppressPlayerBuffParticles(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!((Object) this instanceof net.minecraft.world.entity.player.Player p)) return;
        java.util.List<net.minecraft.core.particles.ParticleOptions> filtered = this.activeEffects.values().stream()
            .filter(net.minecraft.world.effect.MobEffectInstance::isVisible)
            .filter(inst -> {
                var eff = inst.getEffect();
                return !eff.equals(net.minecraft.world.effect.MobEffects.SPEED)
                    && !eff.equals(net.minecraft.world.effect.MobEffects.HASTE)
                    && !eff.equals(net.minecraft.world.effect.MobEffects.STRENGTH)
                    && !eff.equals(net.minecraft.world.effect.MobEffects.REGENERATION);
            })
            .map(net.minecraft.world.effect.MobEffectInstance::getParticleOptions)
            .toList();
        p.getEntityData().set(DATA_EFFECT_PARTICLES, filtered);
    }

    @Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"))
    private void infinium$preArmor(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
        this.infinium$incoming = damage;
        this.infinium$source = source;
    }

    // Full set: scale post-magic damage so a small hit matches the netherite hit-count ratios. Floor is 10% of pre-armor incoming.
    @ModifyReturnValue(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"))
    private float infinium$taken(float taken) {
        float incoming = this.infinium$incoming;
        this.infinium$incoming = -1F;
        if (incoming < 0F || taken <= 0F) return taken;
        SetLook look = SetLook.of(infinium$self());
        if (look == null) return taken;
        // Pass2 fix: Infinium toughness 5 already lowers a zombie hit a little vs netherite toughness 3 (armor 20 does not clamp
        // for small hits), so divide that out to land on the exact netherite ratios. Armor-bypassing damage gets no correction.
        DamageSource src = this.infinium$source;
        this.infinium$source = null;
        double ratio = PerkValues.damageTaken(look.family());
        if (src != null && !src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
            float neth = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(infinium$self(), incoming, src, 20F, 3F);
            float inf = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(infinium$self(), incoming, src, 20F, 5F);
            if (inf > 0F) ratio *= neth / inf;
        }
        float scaled = taken * (float) ratio;
        return Math.max(scaled, incoming * 0.10F);
    }

    // Helmet: unlimited water breathing (Infinium or Infinity helmet, not disabled, perk on)
    @Inject(method = "canBreatheUnderwater", at = @At("RETURN"), cancellable = true)
    private void infinium$breathe(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity e = infinium$self();
        if (!cir.getReturnValue() && Infinium.wearingPiece(e, EquipmentSlot.HEAD) && Perks.on(e, infinium$piece(e, EquipmentSlot.HEAD))) cir.setReturnValue(true);
    }

    // Boots: no fall damage
    @Inject(method = "calculateFallDamage", at = @At("HEAD"), cancellable = true)
    private void infinium$fall(double dist, float mod, CallbackInfoReturnable<Integer> cir) {
        LivingEntity e = infinium$self();
        if ((Infinium.wearingPiece(e, EquipmentSlot.FEET) && Perks.on(e, infinium$piece(e, EquipmentSlot.FEET))) || Infinium.wearing(e, EquipmentSlot.FEET, Infinium.BREAD_BOOTS)) cir.setReturnValue(0);
    }

    // Full set (any 4 Infinium/Infinity pieces): fire/lava immunity; explosions only for the Infinity set
    @Inject(method = "isInvulnerableTo", at = @At("RETURN"), cancellable = true)
    private void infinium$immune(ServerLevel level, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && ((source.is(DamageTypeTags.IS_FIRE) && SetBonuses.immune(infinium$self())) || (source.is(DamageTypeTags.IS_EXPLOSION) && SetBonuses.explosionImmune(infinium$self()))))
            cir.setReturnValue(true);
    }

    // Leggings: ground speed (velocity, not attribute -> no FOV change)
    @Inject(method = "getFrictionInfluencedSpeed", at = @At("RETURN"), cancellable = true)
    private void infinium$landSpeed(float friction, CallbackInfoReturnable<Float> cir) {
        LivingEntity e = infinium$self();
        if (e.onGround() && Infinium.wearingPiece(e, EquipmentSlot.LEGS)) cir.setReturnValue(cir.getReturnValue() * Infinium.legSpeedFactor(e));
    }

    // Leggings: swim/water speed
    @ModifyArg(method = "travelInWater", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"), index = 0)
    private float infinium$waterSpeed(float speed) {
        return Infinium.wearingPiece(infinium$self(), EquipmentSlot.LEGS) ? speed * Infinium.legSpeedFactor(infinium$self()) : speed;
    }

    // G4b: the old client getJumpPower fallback (added the boots bonus when the set_jump modifier had not synced yet)
    // is removed, so a tap is always a vanilla jump. client.VariableJump adds the held-jump height in both cases.

    // Never-break (13.5): a disabled piece/tool contributes no attribute modifiers (armor, toughness, KB, attack)
    @WrapOperation(method = "collectEquipmentChanges", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isBroken()Z"))
    private boolean infinium$disabledIsBroken(ItemStack s, Operation<Boolean> op) { return op.call(s) || InfiniumDurability.disabled(s); }

    // Never-break: no gliding on a disabled chestplate
    @ModifyReturnValue(method = "canGlideUsing", at = @At("RETURN"))
    private static boolean infinium$glide(boolean rv, ItemStack stack, EquipmentSlot slot) { return rv; }   // R4: wings are a perk, kept when broken

    // Tier 2/3 rocket-free elytra flight: holding the flight thrust key ramps toward vanilla rocket speed (InfinityFlight)
    @WrapOperation(method = "travelFallFlying", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;updateFallFlyingMovement(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private net.minecraft.world.phys.Vec3 infinium$fallFlyMovement(LivingEntity entity, net.minecraft.world.phys.Vec3 delta, Operation<net.minecraft.world.phys.Vec3> op) {
        net.minecraft.world.phys.Vec3 motion = op.call(entity, delta);
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            return dev.justin.infinium.InfinityFlight.handleFlightThrust(player, motion);
        }
        return motion;
    }

    // Equip sounds: the piece's family sound, or the set's full-set sound when this equip completes a set; silence when it grants an advancement
    @WrapOperation(method = "onEquipItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V"))
    private void infinium$equipSound(Level level, Entity src, double x, double y, double z, Holder<SoundEvent> sound, SoundSource ss, float vol, float pitch, long seed,
                                     Operation<Void> op, @Local(argsOnly = true) EquipmentSlot slot, @Local(argsOnly = true, ordinal = 1) ItemStack next) {
        Holder<SoundEvent> s = sound;
        if ((Object) this instanceof ServerPlayer sp) {
            s = SetBonuses.equipSound(sp, slot, next, sound);
        } else if ((Object) this instanceof net.minecraft.world.entity.LivingEntity le && le.level().isClientSide()) {
            s = SetBonuses.clientEquipSound(le, slot, next, sound);
        }
        if (s == null) return;
        op.call(level, src, x, y, z, s, ss, vol, pitch, seed);
    }
    // Dynamic Mob Invincibility Window (I-Frame) Compression:
    // When attacked by Cybernetic, CyberPsycho, or Infinity wearer, compress invulnerableTime proportionally to player attack speed
    @Inject(method = "hurtServer", at = @At("TAIL"))
    private void infinium$compressIframes(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity le) {
            SetLook look = SetLook.of(le);
            if (look == SetLook.CYBERNETIC || look == SetLook.CYBERPSYCHO || look == SetLook.INFINITY) {
                double attackBonus = SetBonuses.attack(le, look);
                if (attackBonus > 0) {
                    float speedRatio = 1.0f + (float) attackBonus;
                    int comp = Math.max(1, Math.round(10.0f / speedRatio));
                    LivingEntity victim = infinium$self();
                    victim.invulnerableTime = 10 + comp;
                    victim.hurtDuration = comp;
                    victim.hurtTime = Math.min(victim.hurtTime, comp);
                }
            }
        }
    }
}
