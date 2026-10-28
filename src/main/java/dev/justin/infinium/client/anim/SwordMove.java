package dev.justin.infinium.client.anim;

/**
 * The move a swing animates. Decided ONLY from the energy slash payload that was actually sent
 * (SwordSlashClientHandler.emitSlash), or PLAIN when a swing happened without any emission.
 */
public enum SwordMove {
    /** roll -35, arc axis "\" : blade travels upper-left -> lower-right (combo stage 0, and the early-stab fallback). */
    LEFT_CUT,
    /** roll +35, arc axis "/" : blade travels upper-right -> lower-left (combo stage 1). */
    RIGHT_CUT,
    /** isStab=true : straight laser lance, forward thrust (combo stage 2). */
    STAB,
    /** roll 90, vertical arc : top-down chop (jump crit / Punchy crit). */
    OVERHEAD_CLEAVE,
    /** roll 0, horizontal arc : flat sweep right -> left (crouch attack). */
    FLAT_SWEEP,
    /** Swing with no slash emitted (cooldown/throttle, no armor tier, non-energy mode, block/mining). */
    PLAIN;

    /** Maps the exact payload values to a move. Unknown rolls fall back to the nearest arc. */
    public static SwordMove fromPayload(float roll, boolean isStab) {
        if (isStab) return STAB;
        if (Math.abs(roll) >= 60F) return OVERHEAD_CLEAVE;
        if (roll <= -10F) return LEFT_CUT;
        if (roll >= 10F) return RIGHT_CUT;
        return FLAT_SWEEP;
    }
}
