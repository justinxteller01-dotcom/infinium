package dev.justin.infinium.client;

import dev.justin.infinium.SetBonuses;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * G4b variable jump (Justin 2026-10-27 11:39). The server still puts the perk bonus (boots jump or set Jump, M5 toggles
 * respected) on JUMP_STRENGTH as the set_jump modifier, which syncs to this client. Takeoff here is cut back to vanilla
 * power (tap = ~1.25 blocks). While jump stays held and the player is rising, vertical speed is kept at no less than the
 * full-power jump's speed for the same tick, so holding all the way reaches exactly the old perk height. Releasing jump
 * ends it at once (normal gravity from the current speed). Client-authoritative movement, so the server sees normal
 * vanilla-range player movement and never corrects it. Perk off = no modifier = vanilla jump.
 */
public final class VariableJump {
    private static boolean active;
    private static double virtualVy;

    /** After LivingEntity.jumpFromGround on the local player. */
    public static void onJump(LocalPlayer p) {
        active = false;
        var inst = p.getAttribute(Attributes.JUMP_STRENGTH);
        if (inst == null) return;
        AttributeModifier m = inst.getModifier(SetBonuses.JUMP);
        // Bonus = synced server modifier, or the client-side boots value if it has not synced yet (then the takeoff
        // already used vanilla power and only the held target is raised).
        double bonus = m != null ? m.amount() : SetBonuses.bootJump(p);
        double total = inst.getValue();
        if (bonus <= 0 || total <= 0) return;
        Vec3 v = p.getDeltaMovement();
        double raw = v.y;
        double factor = (raw - p.getJumpBoostPower()) / total;   // block jump factor (honey etc.)
        if (factor <= 0) return;
        double tap = m != null ? raw - bonus * factor : raw;
        double full = m != null ? raw : raw + bonus * factor;
        p.setDeltaMovement(v.x, tap, v.z);
        virtualVy = full;
        active = true;
    }

    /** Start of LocalPlayer.aiStep, before this tick's travel. */
    public static void tick(LocalPlayer p) {
        if (!active) return;
        virtualVy = (virtualVy - 0.08) * 0.98;   // the full-power jump's speed after one more travel tick
        Vec3 v = p.getDeltaMovement();
        boolean held = p.input != null && p.input.keyPresses.jump();
        if (!held || virtualVy <= 0 || v.y <= 0 || p.onGround() || p.isInWater() || p.isInLava()
                || p.isFallFlying() || p.getAbilities().flying || p.isPassenger()) {
            active = false;
            return;
        }
        if (v.y < virtualVy) p.setDeltaMovement(v.x, virtualVy, v.z);
    }

    /** An air jump replaces the ground-jump hold boost. */
    public static void cancel() { active = false; }

    private VariableJump() {}
}
