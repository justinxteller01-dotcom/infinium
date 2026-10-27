package dev.justin.infinium;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Per-player perk toggles (R8 13.14): 53 perks = 5 families x 4 per-piece perks + each family's "Full set" group.
 * ORDINALS ARE APPEND-ONLY (they index the disabled-perk bitmask saved on the player). Families: 0 Astral, 1 Cybernetic,
 * 2 Singularity, 3 Cyber Psycho, 4 Infinity (= SetLook ordinal; InfiniumStyle ordinal for the first 4).
 */
public enum Perk {
    // ---- per-piece (family * 4 + piece) ----
    AST_HELMET_BREATH(0, "breath", EquipmentSlot.HEAD), AST_CHEST_KB(0, "kb", EquipmentSlot.CHEST), AST_LEGS_SPEED(0, "legs", EquipmentSlot.LEGS), AST_BOOTS_FALL(0, "fall", EquipmentSlot.FEET),
    CYB_HELMET_BREATH(1, "breath", EquipmentSlot.HEAD), CYB_CHEST_KB(1, "kb", EquipmentSlot.CHEST), CYB_LEGS_SPEED(1, "legs", EquipmentSlot.LEGS), CYB_BOOTS_FALL(1, "fall", EquipmentSlot.FEET),
    SING_HELMET_BREATH(2, "breath", EquipmentSlot.HEAD), SING_CHEST_KB(2, "kb", EquipmentSlot.CHEST), SING_LEGS_SPEED(2, "legs", EquipmentSlot.LEGS), SING_BOOTS_FALL(2, "fall", EquipmentSlot.FEET),
    CP_HELMET_BREATH(3, "breath", EquipmentSlot.HEAD), CP_CHEST_KB(3, "kb", EquipmentSlot.CHEST), CP_LEGS_SPEED(3, "legs", EquipmentSlot.LEGS), CP_BOOTS_FALL(3, "fall", EquipmentSlot.FEET),
    INF_HELMET_BREATH(4, "breath", EquipmentSlot.HEAD), INF_CHEST_KB(4, "kb", EquipmentSlot.CHEST), INF_LEGS_SPEED(4, "legs", EquipmentSlot.LEGS), INF_BOOTS_FALL(4, "fall", EquipmentSlot.FEET),
    // ---- full set ----
    AST_SET_IMMUNE(0, "immune"), AST_SET_REACH(0, "reach"), AST_SET_REGEN(0, "regen"), AST_SET_LIGHT(0, "light"),
    CYB_SET_IMMUNE(1, "immune"), CYB_SET_ATTACK(1, "attack"), CYB_SET_MINING(1, "mining"), CYB_SET_LEGS(1, "setlegs"), CYB_SET_LIGHT(1, "light"),
    SING_SET_IMMUNE(2, "immune"), SING_SET_REACH(2, "reach"), SING_SET_STACKS(2, "stacks"), SING_SET_NOCONSUME(2, "noconsume"), SING_SET_REGEN(2, "regen"), SING_SET_LIGHT(2, "light"),
    CP_SET_IMMUNE(3, "immune"), CP_SET_ATTACK(3, "attack"), CP_SET_MINING(3, "mining"), CP_SET_LEGS(3, "setlegs"), CP_SET_JUMP(3, "jump"), CP_SET_SLOW(3, "slow"), CP_SET_LIGHT(3, "light"),
    INF_SET_IMMUNE(4, "immune"), INF_SET_REACH(4, "reach"), INF_SET_ATTACK(4, "attack"), INF_SET_MINING(4, "mining"), INF_SET_LEGS(4, "setlegs"), INF_SET_JUMP(4, "jump"),
    INF_SET_STACKS(4, "stacks"), INF_SET_NOCONSUME(4, "noconsume"), INF_SET_REGEN(4, "regen"), INF_SET_SLOW(4, "slow"), INF_SET_LIGHT(4, "light"),
    // ---- R2 additions (append-only) ----
    AST_SET_NOCONSUME(0, "noconsume"), AST_SET_STACKS(0, "stacks"), CYB_SET_JUMP(1, "jump"), CYB_SET_SLOW(1, "slow"),
    // ---- M5 additions (append-only; 62 of 64 mask bits used) ----
    // Boots per-piece jump, split from "fall" so each can be toggled (only the families whose boots tooltip lists a jump).
    AST_BOOTS_JUMP(0, "bootjump", EquipmentSlot.FEET), SING_BOOTS_JUMP(2, "bootjump", EquipmentSlot.FEET), INF_BOOTS_JUMP(4, "bootjump", EquipmentSlot.FEET),
    // Piece abilities: one toggle per player across every set (family -1 = "All sets"); id path = Ability id path.
    ABILITY_WARP(-1, "warp", EquipmentSlot.LEGS), ABILITY_XRAY_PULSE(-1, "xray_pulse", EquipmentSlot.HEAD);

    public static final String[] FAMILY_IDS = {"astral", "cybernetic", "singularity", "cyberpsycho", "infinity"};
    public static final String[] FAMILY_NAMES = {"Astral", "Cybernetic", "Singularity", "Cyber Psycho", "Infinity"};
    public final int family; public final String kind; public final EquipmentSlot piece;
    Perk(int family, String kind, EquipmentSlot piece) { this.family = family; this.kind = kind; this.piece = piece; }
    Perk(int family, String kind) { this(family, kind, null); }
    public boolean isSet() { return piece == null; }
    public boolean isAbility() { return family < 0; }
    public String familyName() { return family < 0 ? "All sets" : FAMILY_NAMES[family]; }
    /** M5: the boots-jump toggle for a family, or null if that family's boots have no per-piece jump. */
    public static Perk bootJump(int family) { return switch (family) { case 0 -> AST_BOOTS_JUMP; case 2 -> SING_BOOTS_JUMP; case 4 -> INF_BOOTS_JUMP; default -> null; }; }
    /** M5: the toggle for an Ability id path ("warp", "xray_pulse"), or null. */
    public static Perk ability(String path) {
        for (Perk p : values()) if (p.isAbility() && p.kind.equals(path)) return p;
        return null;
    }
    static { if (values().length > 64) throw new IllegalStateException("Perk mask is a long: max 64 perks"); }
    public long bit() { return 1L << ordinal(); }
    public String translationKey() { return "perk.infinium." + kind; }

    /** Family of an Infinium/Infinity armor piece: Infinity 4, else its style (legacy unstyled = Astral). -1 = not ours. */
    public static int family(ItemStack s) {
        if (Infinium.isInfinity(s)) return 4;
        if (!Infinium.isArmor(s)) return -1;
        InfiniumStyle st = InfiniumStyle.get(s);
        return st == null ? 0 : st.ordinal();
    }
    public static Perk piece(int family, EquipmentSlot slot) {
        if (family < 0) return null;
        int p = switch (slot) { case HEAD -> 0; case CHEST -> 1; case LEGS -> 2; case FEET -> 3; default -> -1; };
        return p < 0 ? null : values()[family * 4 + p];
    }
    /** The full-set perk of this kind for a family, or null if that family has no such perk. */
    public static Perk set(int family, String kind) {
        for (Perk p : values()) if (p.isSet() && p.family == family && p.kind.equals(kind)) return p;
        return null;
    }
    public static Perk set(SetLook look, String kind) { return look == null ? null : set(look.ordinal(), kind); }
}
