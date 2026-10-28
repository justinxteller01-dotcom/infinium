package dev.justin.infinium;

/**
 * One table for perk numbers. Tooltips, the perk screen, and the guide must
 * read these fields so the text cannot drift from the code.
 *
 * All values are backed by InfiniumConfig (config/infinium.json) with fallback defaults.
 */
public final class PerkValues {
    public enum Family {
        ASTRAL, CYBERNETIC, SINGULARITY, CYBERPSYCHO, INFINITY
    }

    /** Gear stops working at this remaining durability and never breaks. */
    public static final int DURABILITY_FLOOR = 50;

    public static final double LEG_SPEED_ASTRAL = 0.05;
    public static final double LEG_SPEED_SINGULARITY = 0.05;
    public static final double LEG_SPEED_CYBERNETIC = 0.10;
    public static final double LEG_SPEED_CYBERPSYCHO = 0.15;
    public static final double LEG_SPEED_INFINITY = 0.20;

    public static final double ATTACK_CYBERNETIC = 0.20;
    public static final double ATTACK_CYBERPSYCHO = 0.50;
    public static final double ATTACK_INFINITY = 0.75;

    public static final double MINING_CYBERNETIC = 0.10;
    public static final double MINING_CYBERPSYCHO = 0.20;
    public static final double MINING_INFINITY = 0.30;

    /** Added to vanilla jump strength 0.42. */
    public static final double JUMP_CYBERNETIC = 0.16310;
    public static final double JUMP_CYBERPSYCHO = 0.29539;
    public static final double JUMP_INFINITY = 0.41076;

    /** Batch1 in-air forward boost (blocks/tick^2 added along movement input while airborne from a jump, boots family).
     *  Air drag 0.91 => steady extra speed ~= value/0.09: Astral ~+0.017, Singularity ~+0.033, Infinity ~+0.056 b/t (~20 % of sprint). */
    public static final double AIR_PUSH_ASTRAL = 0.0015;
    public static final double AIR_PUSH_SINGULARITY = 0.0030;
    public static final double AIR_PUSH_INFINITY = 0.0050;
    public static double airPush(int bootsFamily) {
        return switch (bootsFamily) { case 0 -> AIR_PUSH_ASTRAL; case 2 -> AIR_PUSH_SINGULARITY; case 4 -> AIR_PUSH_INFINITY; default -> 0; };
    }

    public static final double REACH_ASTRAL = 1.0;
    public static final double REACH_SINGULARITY = 2.0;
    public static final double REACH_INFINITY = 3.0;

    /** Chance to keep the stack (not consume it). 0 means the perk is absent. */
    public static final double KEEP_ASTRAL = 0.11;
    public static final double KEEP_SINGULARITY = 0.22;
    public static final double KEEP_INFINITY = 0.33;

    public static final int REPAIR_ASTRAL_TICKS = 200;
    public static final int REPAIR_SINGULARITY_TICKS = 100;
    public static final int REPAIR_INFINITY_TICKS = 40;

    public static final int SLOW_RADIUS = 16;
    public static final double SLOW_CYBERNETIC = 0.25;
    public static final double SLOW_CYBERPSYCHO = 0.50;
    public static final double SLOW_INFINITY = 0.75;

    private PerkValues() {}

    public static int durabilityFloor() {
        return InfiniumConfig.getDurabilityFloor();
    }

    /**
     * Resolves the effective Family for a crystal-gated full-set perk:
     * - Tier 1 (Astral / Cybernetic): returns the family if unlocked by crystals, else null.
     * - Tier 2 (Singularity / Cyber Psycho): returns Tier 2 if unlocked by crystals, else Tier 1 baseline (Astral / Cybernetic).
     * - Tier 3 (Infinity): returns Infinity if unlocked by crystals, else Tier 2 baseline (Singularity / Cyber Psycho).
     */
    public static Family effectiveFamily(net.minecraft.world.entity.LivingEntity e, SetLook look, String kind) {
        if (e == null || look == null) return null;
        if (CrystalCharge.setPerkUnlocked(e, look, kind)) return look.family();
        return switch (look) {
            case ASTRAL, CYBERNETIC -> null;
            case SINGULARITY -> Family.ASTRAL;
            case CYBERPSYCHO -> Family.CYBERNETIC;
            case INFINITY -> switch (kind) {
                case "regen", "noconsume", "reach", "stacks" -> Family.SINGULARITY;
                default -> Family.CYBERPSYCHO;
            };
        };
    }

    /** Short description of a perk for the Crystal Screen milestone markers. */
    public static String shortPerkDesc(int familyOrd, String kind) {
        Family f = Family.values()[familyOrd];
        if (f == Family.ASTRAL) {
            // Astral-only plain wording; other families keep the shared lines below.
            switch (kind) {
                case "regen": return "Auto-Repair";
                case "noconsume": return "Block Saver";
                case "reach": return "+" + (int) reach(f) + " Block Reach";
                case "stacks": return "Bigger Stacks";
                case "saber": return "Energy Slash";
                default: break;
            }
        }
        return switch (kind) {
            case "regen" -> "Auto-Repair (" + (repairTicks(f) / 20) + "s)";
            case "noconsume" -> Math.round(keepChance(f) * 100) + "% Block Saver";
            case "reach" -> "+" + (int) reach(f) + " Block Reach";
            case "stacks" -> "Stacks up to " + boostedMax(f, 64);
            case "mining" -> "+" + Math.round(mining(f) * 100) + "% Mining Speed";
            case "jump" -> "+" + airJumps(f) + " Air Jump" + (airJumps(f) == 1 ? "" : "s");
            case "attack" -> switch (f) {
                case CYBERPSYCHO -> "Faster attacks +" + Math.round(attackAndBow(f) * 100) + "%";
                case INFINITY -> "Faster attacks +" + Math.round(attackAndBow(f) * 100) + "%";
                default -> "Faster attacks +" + Math.round(attackAndBow(f) * 100) + "%";
            };
            case "slow" -> "Slow nearby mobs when damaged";
            case "saber" -> "Energy Slash & Beam";
            default -> kind;
        };
    }

    /** Leggings only. The value is the piece's own family, not the worn set. */
    public static double legSpeed(Family family) {
        return switch (family) {
            case ASTRAL -> InfiniumConfig.getLegSpeedAstral();
            case SINGULARITY -> InfiniumConfig.getLegSpeedSingularity();
            case CYBERNETIC -> InfiniumConfig.getLegSpeedCybernetic();
            case CYBERPSYCHO -> InfiniumConfig.getLegSpeedCyberpsycho();
            case INFINITY -> InfiniumConfig.getLegSpeedInfinity();
        };
    }

    public static double attackAndBow(Family family) {
        return switch (family) {
            case CYBERNETIC -> InfiniumConfig.getAttackBonusT1();
            case CYBERPSYCHO -> InfiniumConfig.getAttackBonusT2();
            case INFINITY -> InfiniumConfig.getAttackBonusT3();
            default -> 0;
        };
    }

    public static double mining(Family family) {
        return switch (family) {
            case CYBERNETIC -> InfiniumConfig.getMiningBonusT1();
            case CYBERPSYCHO -> InfiniumConfig.getMiningBonusT2();
            case INFINITY -> InfiniumConfig.getMiningBonusT3();
            default -> 0;
        };
    }

    /** G4b: set Jump perk = number of mid-air jumps (config air_jumps_t1..t3). */
    public static int airJumps(Family family) {
        return switch (family) {
            case CYBERNETIC -> InfiniumConfig.getAirJumpsT1();
            case CYBERPSYCHO -> InfiniumConfig.getAirJumpsT2();
            case INFINITY -> InfiniumConfig.getAirJumpsT3();
            default -> 0;
        };
    }

    public static String airJumpsText(Family family) {
        int n = airJumps(family);
        return "+" + n + " air jump" + (n == 1 ? "" : "s");
    }

    public static double reach(Family family) {
        return switch (family) {
            case ASTRAL -> InfiniumConfig.getReachBonusT1();
            case SINGULARITY -> InfiniumConfig.getReachBonusT2();
            case INFINITY -> InfiniumConfig.getReachBonusT3();
            default -> 0;
        };
    }

    public static double keepChance(Family family) {
        return switch (family) {
            case ASTRAL -> InfiniumConfig.getKeepChanceT1();
            case SINGULARITY -> InfiniumConfig.getKeepChanceT2();
            case INFINITY -> InfiniumConfig.getKeepChanceT3();
            default -> 0;
        };
    }

    /** 0 means this set does not repair. */
    public static int repairTicks(Family family) {
        return switch (family) {
            case ASTRAL -> InfiniumConfig.getRepairIntervalT1() * 20;
            case SINGULARITY -> InfiniumConfig.getRepairIntervalT2() * 20;
            case INFINITY -> InfiniumConfig.getRepairIntervalT3() * 20;
            default -> 0;
        };
    }

    public static double slowAmount(Family family) {
        return switch (family) {
            case CYBERNETIC -> InfiniumConfig.getSlowAuraT1();
            case CYBERPSYCHO -> InfiniumConfig.getSlowAuraT2();
            case INFINITY -> InfiniumConfig.getSlowAuraT3();
            default -> 0;
        };
    }

    /** batch1 D: slow duration / cooldown in seconds by tier (Cybernetic, Cyber Psycho, Infinity). */
    public static final int[] SLOW_DURATION_SEC = {2, 4, 6};
    public static final int[] SLOW_COOLDOWN_SEC = {12, 10, 8};

    static int slowTierIndex(Family family) {
        return switch (family) { case CYBERPSYCHO -> 1; case INFINITY -> 2; default -> 0; };
    }

    public static int slowDurationTicks(Family family) { return SLOW_DURATION_SEC[slowTierIndex(family)] * 20; }

    public static int slowCooldownTicks(Family family) { return SLOW_COOLDOWN_SEC[slowTierIndex(family)] * 20; }

    public static int slowRadius() {
        return InfiniumConfig.getSlowRadius();
    }

    public static double damageTaken(Family family) {
        return switch (family) {
            case ASTRAL, CYBERNETIC -> InfiniumConfig.getDamageTakenT1();
            case SINGULARITY, CYBERPSYCHO -> InfiniumConfig.getDamageTakenT2();
            case INFINITY -> InfiniumConfig.getDamageTakenT3();
        };
    }

    public static String damageTakenText(Family family) {
        double d = damageTaken(family) * 100.0;
        return String.format(java.util.Locale.ROOT, "%.1f%%", d);
    }

    public static boolean explosionImmunity(Family family) {
        return family == Family.INFINITY;
    }

    public static int boostedMax(Family family, int vanillaMax) {
        if (family == null || vanillaMax <= 0) return vanillaMax;
        if (vanillaMax <= 1) {
            return switch (family) {
                case ASTRAL -> 2;
                case SINGULARITY -> 4;
                case INFINITY -> 16;
                default -> vanillaMax;
            };
        }
        if (vanillaMax <= 16) {
            return switch (family) {
                case ASTRAL -> 24;
                case SINGULARITY -> 32;
                case INFINITY -> 64;
                default -> vanillaMax;
            };
        }
        return switch (family) {
            case ASTRAL -> 96;
            case SINGULARITY -> 128;
            case INFINITY -> 256;
            default -> vanillaMax;
        };
    }
}
