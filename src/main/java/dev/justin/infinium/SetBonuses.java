package dev.justin.infinium;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Full-set bonuses (server-authoritative): transient attribute modifiers (reach, attack speed, mining, jump, KB-off compensation),
 * durability regen, the Cyber Psycho slow aura, set transitions (advancements, full-set sound). R3-R8 rules, 12.3 / 13.6 / 13.14.
 */
public final class SetBonuses {
    public static final Identifier REACH_B = Infinium.id("set_reach_block"), REACH_E = Infinium.id("set_reach_entity"),
        ATTACK = Infinium.id("set_attack_speed"), MINING = Infinium.id("set_mining"), JUMP = Infinium.id("set_jump"),
        SAFE_FALL = Infinium.id("boots_safe_fall"), KB_OFF = Infinium.id("kb_off");
    public static Holder<MobEffect> CP_SLOW;
    /** Last settled look per player (Optional.empty() = none). Absent = not settled yet (login/respawn: settle silently). */
    private static final java.util.Map<java.util.UUID, java.util.Optional<SetLook>> LAST = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Long> SLOW_LAST = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Long> FULL_SET_SOUND_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<java.util.UUID> DIRTY = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static void markDirty(ServerPlayer p) { DIRTY.add(p.getUUID()); }

    public static void register() {
        CP_SLOW = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Infinium.id("cyberpsycho_slow"), new CyberPsychoSlowEffect());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) tick(p);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> { LAST.remove(h.player.getUUID()); SLOW_LAST.remove(h.player.getUUID()); });
        ServerPlayerEvents.AFTER_RESPAWN.register((o, n, alive) -> LAST.remove(n.getUUID()));
        ServerPlayerEvents.JOIN.register(p -> LAST.remove(p.getUUID()));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((e, src, base, taken, blocked) -> { if (taken > 0 && e instanceof ServerPlayer sp) slowAura(sp); });
    }

    // ---------- bonus values (also read on the client for prediction: bow draw, light) ----------
    // R2: all numbers come from PerkValues; each value is gated by that set's own toggle, with Tier 2 / Infinity inheriting baseline tier stats prior to crystal milestones.
    static boolean has(LivingEntity e, SetLook l, String kind) { Perk p = Perk.set(l, kind); return l != null && p != null && Perks.on(e, p); }
    static PerkValues.Family eff(LivingEntity e, SetLook l, String kind) { return has(e, l, kind) ? PerkValues.effectiveFamily(e, l, kind) : null; }
    public static double reach(LivingEntity e, SetLook l) { var f = eff(e, l, "reach"); return f != null ? PerkValues.reach(f) : 0; }
    public static double attack(LivingEntity e, SetLook l) { var f = eff(e, l, "attack"); return f != null ? PerkValues.attackAndBow(f) : 0; }
    public static double attack(LivingEntity e) { return attack(e, SetLook.of(e)); }
    static double mining(LivingEntity e, SetLook l) { var f = eff(e, l, "mining"); return f != null ? PerkValues.mining(f) : 0; }
    /** G4b: set Jump perk = air jumps (M5 toggle respected in has()). Both sides. */
    public static int airJumps(LivingEntity e) { var f = eff(e, SetLook.of(e), "jump"); return f != null ? PerkValues.airJumps(f) : 0; }
    public static double bootJump(Player p) {
        if (!Infinium.wearingPiece(p, EquipmentSlot.FEET)) return 0;
        ItemStack boots = p.getItemBySlot(EquipmentSlot.FEET);
        int fam = Perk.family(boots);
        Perk jp = Perk.bootJump(fam);   // M5: own toggle, separate from fall
        if (jp == null || InfiniumDurability.disabled(boots) || !Perks.on(p, jp)) return 0;
        return Infinium.bootJumpBoost(boots);
    }
    public static double bootSafeFall(Player p) {
        if (!Infinium.wearingPiece(p, EquipmentSlot.FEET)) return 0;
        ItemStack boots = p.getItemBySlot(EquipmentSlot.FEET);
        int fam = Perk.family(boots);
        if (fam < 0 || InfiniumDurability.disabled(boots) || !Perks.on(p, Perk.piece(fam, EquipmentSlot.FEET))) return 0;
        return Infinium.bootSafeFallDistance(boots);
    }
    /** LambDynamicLights level: Infinity 12, Singularity / Cyber Psycho 8, Astral / Cybernetic 4 (light perk of the current set). */
    public static int light(LivingEntity e) {
        SetLook l = SetLook.of(e);
        if (l == null || !Perks.on(e, Perk.set(l, "light"))) return 0;
        return l == SetLook.INFINITY ? 12 : (l == SetLook.SINGULARITY || l == SetLook.CYBERPSYCHO) ? 8 : 4;
    }
    public static int regenInterval(LivingEntity e, SetLook l) { var f = eff(e, l, "regen"); return f != null ? PerkValues.repairTicks(f) : 0; }
    public static float keepChance(LivingEntity e) { SetLook l = SetLook.of(e); var f = eff(e, l, "noconsume"); return f != null ? (float) PerkValues.keepChance(f) : 0F; }
    public static boolean noConsume(LivingEntity e) { return keepChance(e) > 0; }
    /** Fire and lava: any 4 Infinium/Infinity pieces; the current set's immunity toggle applies when a set qualifies. */
    public static boolean immune(LivingEntity e) {
        if (!Infinium.fullSet(e)) return false;
        SetLook l = SetLook.of(e);
        return l == null || Perks.on(e, Perk.set(l, "immune"));
    }
    /** Explosions: Infinity full set only (R2 final override). */
    public static boolean explosionImmune(LivingEntity e) { SetLook l = SetLook.of(e); return l == SetLook.INFINITY && has(e, l, "immune"); }

    static void set(LivingEntity e, Holder<Attribute> a, Identifier id, double v, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(id);
        if (v == 0) { if (cur != null) inst.removeModifier(id); return; }
        if (cur != null && cur.amount() == v && cur.operation() == op) return;
        inst.addOrUpdateTransientModifier(new AttributeModifier(id, v, op));
    }

    static void tick(ServerPlayer p) {
        if (!p.isAlive()) return;
        SetLook l = SetLook.of(p);
        // attributes (value-compared: no resend unless something changed)
        double r = reach(p, l);
        set(p, Attributes.BLOCK_INTERACTION_RANGE, REACH_B, r, AttributeModifier.Operation.ADD_VALUE);
        set(p, Attributes.ENTITY_INTERACTION_RANGE, REACH_E, r, AttributeModifier.Operation.ADD_VALUE);
        set(p, Attributes.ATTACK_SPEED, ATTACK, attack(p, l), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(p, Attributes.BLOCK_BREAK_SPEED, MINING, mining(p, l), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        double jb = bootJump(p);   // G4b: height comes from Astral/Singularity/Infinity boots only; the set Jump perk is air jumps
        set(p, Attributes.JUMP_STRENGTH, JUMP, jb, AttributeModifier.Operation.ADD_VALUE);
        set(p, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL, bootSafeFall(p), AttributeModifier.Operation.ADD_VALUE);
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        boolean kbOff = Infinium.wearingPiece(p, EquipmentSlot.CHEST) && !Perks.on(p, Perk.piece(Perk.family(chest), EquipmentSlot.CHEST));
        set(p, Attributes.KNOCKBACK_RESISTANCE, KB_OFF, kbOff ? -Infinium.CHEST_KB : 0, AttributeModifier.Operation.ADD_VALUE);
        DIRTY.remove(p.getUUID());
        // set transitions (advancements; the full-set sound is played from the equip hook)
        java.util.Optional<SetLook> prev = LAST.get(p.getUUID());
        if (prev == null) LAST.put(p.getUUID(), java.util.Optional.ofNullable(l));          // settle silently
        else if (prev.orElse(null) != l) {
            LAST.put(p.getUUID(), java.util.Optional.ofNullable(l));
            if (l != null) awardFor(p, l);
        }
        // regen: 1 point per eligible single damageable item per pulse.
        // Slots 0..40, plus offhand and every equipment slot (identity-deduped), plus compat hooks (REPAIR_HOOKS:
        // Traveler's Backpack worn/carried storage + tool slots, Accessories slots). Hooks are fail-safe.
        int iv = regenInterval(p, l);
        if (iv > 0 && !p.isSpectator() && (p.level().getGameTime() + p.getId()) % iv == 0) {
            Inventory inv = p.getInventory();
            boolean ch = false;
            java.util.IdentityHashMap<ItemStack, Boolean> seen = new java.util.IdentityHashMap<>();
            int n = Math.min(41, inv.getContainerSize());
            for (int i = 0; i < n; i++) {
                ItemStack s = inv.getItem(i);
                if (s == null || s.isEmpty() || seen.put(s, Boolean.TRUE) != null) continue;
                if (s.getCount() != 1 || !s.isDamageableItem() || !s.isDamaged()) continue;
                s.setDamageValue(s.getDamageValue() - 1);
                ch = true;
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack s = p.getItemBySlot(slot);
                if (s == null || s.isEmpty() || seen.put(s, Boolean.TRUE) != null) continue;
                if (s.getCount() != 1 || !s.isDamageableItem() || !s.isDamaged()) continue;
                s.setDamageValue(s.getDamageValue() - 1);
                ch = true;
            }
            for (var hook : REPAIR_HOOKS) { try { ch |= hook.test(p); } catch (Throwable ignored) {} }
            if (ch) {
                inv.setChanged();
                p.inventoryMenu.broadcastChanges();
                p.containerMenu.broadcastChanges();
            }
        }
    }

    /** pass2: compat repair passes (registered only when the mod is loaded). Each returns true when it changed something. */
    public static final java.util.List<java.util.function.Predicate<ServerPlayer>> REPAIR_HOOKS = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** One point off a single damaged damageable stack, as a new copy (null = not eligible). Callers write it back through the owner's API. */
    public static ItemStack repairedCopy(ItemStack s) {
        if (s == null || s.isEmpty() || s.getCount() != 1 || !s.isDamageableItem() || !s.isDamaged()) return null;
        ItemStack c = s.copy();
        c.setDamageValue(c.getDamageValue() - 1);
        return c;
    }

    /** Award every advancement whose set newly qualifies (Infinity also backfills Singularity and Cyber Psycho). true = any new. */
    static boolean awardFor(ServerPlayer p, SetLook l) {
        boolean any = false;
        any |= InfiniumAdvancements.award(p, "full_set");
        if (l == SetLook.INFINITY) { any |= award(p, "infinity_set"); any |= award(p, "singularity_set"); any |= award(p, "cyberpsycho_set"); }
        else any |= award(p, l.id + "_set");

        // Award max-charge advancement if this set is already at full capacity
        int filled = CrystalCharge.setFilled(p, l);
        int cap = CrystalCharge.setSlots(l);
        if (filled >= cap && cap > 0) {
            switch (l) {
                case ASTRAL -> any |= InfiniumAdvancements.award(p, "astral_max_charge");
                case CYBERNETIC -> any |= InfiniumAdvancements.award(p, "cybernetic_max_charge");
                case SINGULARITY -> any |= InfiniumAdvancements.award(p, "singularity_max_charge");
                case CYBERPSYCHO -> any |= InfiniumAdvancements.award(p, "cyberpsycho_max_charge");
                case INFINITY -> any |= InfiniumAdvancements.award(p, "infinity_max_charge");
            }
        }
        return any;
    }
    static boolean award(ServerPlayer p, String n) {
        return InfiniumAdvancements.award(p, n);
    }

    /** Equip sound (server, from LivingEntity.onEquipItem). null = play nothing (advancement toast only). */
    public static Holder<SoundEvent> equipSound(ServerPlayer p, EquipmentSlot slot, ItemStack stack, Holder<SoundEvent> vanilla) {
        int fam = Perk.family(stack);
        if (fam < 0 || slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return vanilla;
        long tick = p.level().getGameTime();
        Long lastFullTick = FULL_SET_SOUND_TICK.get(p.getUUID());
        if (lastFullTick != null && tick - lastFullTick < 30L) return null;        // full-set sound already triggered within 1.5s
        SetLook prevLook = SetLook.of(p);
        SetLook now = SetLook.lookWith(p, slot, stack);
        java.util.Optional<SetLook> prev = LAST.get(p.getUUID());
        if (prev == null) {
            LAST.put(p.getUUID(), java.util.Optional.ofNullable(now));
            return ModSounds.equip(fam);                       // not settled (login/respawn): piece sound only
        }
        LAST.put(p.getUUID(), java.util.Optional.ofNullable(now));
        if (now != null && (prev.orElse(null) != now || prevLook != now)) {
            FULL_SET_SOUND_TICK.put(p.getUUID(), tick);
            awardFor(p, now);                                         // toast still shows; its chime is swallowed client-side
            return ModSounds.setEquip(now);                            // full set: set sound only, not the piece sound on top
        }
        return ModSounds.equip(fam);
    }

    private static long clientLastSetTick = -1;

    /** Client equip sound: client-side equivalent so player immediately hears full set fanfare when equipping the final piece. */
    public static Holder<SoundEvent> clientEquipSound(net.minecraft.world.entity.LivingEntity le, EquipmentSlot slot, ItemStack stack, Holder<SoundEvent> vanilla) {
        int fam = Perk.family(stack);
        if (fam < 0 || slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return vanilla;
        SetLook prev = SetLook.of(le);
        SetLook nextLook = SetLook.lookWith(le, slot, stack);
        if (nextLook != null && prev != nextLook) {
            long tick = le.level().getGameTime();
            if (clientLastSetTick >= 0 && tick - clientLastSetTick < 30L) return null;
            clientLastSetTick = tick;
            return ModSounds.setEquip(nextLook);
        }
        return ModSounds.equip(fam);
    }

    /** R2: Cybernetic 25 %, Cyber Psycho 50 %, Infinity 75 %: every hostile within 16 blocks when the wearer takes damage; 2/4/6 s, cooldown 12/10/8 s (PerkValues, batch1 D). */
    static void slowAura(ServerPlayer p) {
        SetLook l = SetLook.of(p);
        var f = eff(p, l, "slow");
        if (f == null) return;
        double amt = PerkValues.slowAmount(f);
        if (amt <= 0) return;
        long now = p.level().getGameTime();
        Long last = SLOW_LAST.get(p.getUUID());
        if (last != null && now - last < PerkValues.slowCooldownTicks(f)) return;
        SLOW_LAST.put(p.getUUID(), now);
        int amp = (int) Math.round(amt / 0.25) - 1;   // effect is -25 % movement per level
        int slowRad = PerkValues.slowRadius();
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(slowRad),
                m -> m.isAlive() && m instanceof net.minecraft.world.entity.monster.Enemy && m.distanceToSqr(p) <= slowRad * slowRad))
            m.addEffect(new MobEffectInstance(CP_SLOW, PerkValues.slowDurationTicks(f), amp, true, false, false), p);
    }
    private SetBonuses() {}
}
