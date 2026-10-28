package dev.justin.infinium;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Gear sounds + crystal sounds + meteor + wings toggle + tool mode + flight audio. */
public final class ModSounds {
    public static final String[] EVENTS = {"pickup", "place", "equip", "set_equip"};
    /** [family 0..4][event 0..3] */
    public static final Holder.Reference<SoundEvent>[][] ARMOR = new Holder.Reference[5][4];
    public static Holder.Reference<SoundEvent> TOOL_PICKUP, TOOL_PLACE;
    public static Holder.Reference<SoundEvent> TOOL_MODE;

    /** Wings toggle sounds: [family 0..4][0=enable, 1=disable] */
    public static final Holder.Reference<SoundEvent>[][] WINGS_TOGGLE = new Holder.Reference[5][2];

    /** Flight sound events: [family 0..4]: 0=Astral, 2=Singularity, 4=Infinity */
    public static final Holder.Reference<SoundEvent>[] FLIGHT_ENTER = new Holder.Reference[5];
    public static final Holder.Reference<SoundEvent>[] FLIGHT_THRUST = new Holder.Reference[5];

    /** Spacetime Warp sound events (Batch 01) */
    public static Holder.Reference<SoundEvent> WARP_DEPARTURE, WARP_ARRIVAL;
    /** batch1: Cyber Psycho three-part thrust audio. sounds.json points these at PLACEHOLDER vanilla events (gen_lang_extra.py)
     *  until the real clips are dropped in. */
    public static Holder.Reference<SoundEvent> CP_THRUST_START, CP_THRUST_LOOP, CP_THRUST_STOP;
    /** G4b air jumps: every air jump / the one that uses the last air jump (shared by all tiers). Placeholders until S1. */
    public static Holder.Reference<SoundEvent> BONUS_JUMP, BONUS_JUMP_LAST;

    /** Sword Energy Slash & Stab sound events */
    public static Holder.Reference<SoundEvent> SWORD_SLASH_LAUNCH, SWORD_STAB_LAUNCH, SWORD_SLASH_CHARGE, SWORD_STAB_CHARGE, SWORD_SPIN, SWORD_SLASH_BLOCK_HIT;
    /** Themed sword sounds: [family 0..4][0..8 kinds] = infinium:sword.<set>.<kind>, files sounds/sword/<set>_<kind>.ogg. */
    public static final String[] SWORD_KINDS = {
        "slash_launch", "stab_launch", "slash_charge", "stab_charge", "spin", "crit_twirl", "block_hit", "entity_hit", "mode_toggle"
    };
    public static final int SLASH_LAUNCH = 0, STAB_LAUNCH = 1, SLASH_CHARGE = 2, STAB_CHARGE = 3,
                            SPIN = 4, CRIT_TWIRL = 5, BLOCK_HIT = 6, ENTITY_HIT = 7, MODE_TOGGLE = 8;
    public static final Holder.Reference<SoundEvent>[][] SWORD = new Holder.Reference[5][9];

    /** Meteor shower sound events */
    public static Holder.Reference<SoundEvent> METEOR_FALLING, METEOR_IMPACT, METEOR_WARNING, METEOR_COUNTDOWN, METEOR_AMBIENT;
    /** Crystal sounds: [family 0..4][INSERT, PERK, FULL, DENIED] = infinium:crystal.<set>.<kind>, files sounds/crystal/<set>_<kind>.ogg. */
    public static final String[] CRYSTAL_KINDS = {"insert", "perk", "full", "denied"};
    public static final int INSERT = 0, PERK = 1, FULL = 2, DENIED = 3;
    public static final Holder.Reference<SoundEvent>[][] CRYSTAL = new Holder.Reference[5][4];
    /** batch1: per-set perk unlock/upgrade cue and one-time "set maxed" cue, [family 0..4]. Placeholders (vanilla events) in sounds.json. */
    public static final Holder.Reference<SoundEvent>[] PERK_UNLOCK = new Holder.Reference[5];
    public static final Holder.Reference<SoundEvent>[] SET_MAXED = new Holder.Reference[5];
    static Holder.Reference<SoundEvent> reg(String n) {
        Identifier id = Infinium.id(n);
        return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
    public static void register() {
        for (int f = 0; f < 5; f++) for (int e = 0; e < 4; e++) ARMOR[f][e] = reg(Perk.FAMILY_IDS[f] + "_" + EVENTS[e]);
        for (int f = 0; f < 5; f++) for (int k = 0; k < 4; k++) CRYSTAL[f][k] = reg("crystal." + Perk.FAMILY_IDS[f] + "." + CRYSTAL_KINDS[k]);
        for (int f = 0; f < 5; f++) {
            PERK_UNLOCK[f] = reg("perk_unlock_" + Perk.FAMILY_IDS[f]);
            SET_MAXED[f] = reg("set_maxed_" + Perk.FAMILY_IDS[f]);
        }
        TOOL_PICKUP = reg("tool_pickup"); TOOL_PLACE = reg("tool_place");
        TOOL_MODE = reg("tool_mode");

        for (int f = 0; f < 5; f++) {
            WINGS_TOGGLE[f][0] = reg("wings_" + Perk.FAMILY_IDS[f] + "_enable");
            WINGS_TOGGLE[f][1] = reg("wings_" + Perk.FAMILY_IDS[f] + "_disable");
        }

        FLIGHT_ENTER[0] = reg("flight_enter_astral");
        FLIGHT_ENTER[1] = FLIGHT_ENTER[0];
        FLIGHT_ENTER[2] = reg("flight_enter_singularity");
        FLIGHT_ENTER[3] = FLIGHT_ENTER[2];
        FLIGHT_ENTER[4] = reg("flight_enter_infinity");

        FLIGHT_THRUST[0] = reg("flight_thrust_astral");
        FLIGHT_THRUST[1] = FLIGHT_THRUST[0];
        FLIGHT_THRUST[2] = reg("flight_thrust_singularity");
        FLIGHT_THRUST[3] = FLIGHT_THRUST[2];
        FLIGHT_THRUST[4] = reg("flight_thrust_infinity");

        CP_THRUST_START = reg("cyberpsycho_thrust_start");
        CP_THRUST_LOOP = reg("cyberpsycho_thrust_loop");
        CP_THRUST_STOP = reg("cyberpsycho_thrust_stop");
        WARP_DEPARTURE = reg("warp_departure");
        WARP_ARRIVAL = reg("warp_arrival");
        BONUS_JUMP = reg("bonus_jump");
        BONUS_JUMP_LAST = reg("bonus_jump_last");

        SWORD_SLASH_LAUNCH = reg("sword_slash_launch");
        SWORD_STAB_LAUNCH = reg("sword_stab_launch");
        SWORD_SLASH_CHARGE = reg("sword_slash_charge");
        SWORD_STAB_CHARGE = reg("sword_stab_charge");
        SWORD_SPIN = reg("sword_spin");
        SWORD_SLASH_BLOCK_HIT = reg("sword_slash_block_hit");

        for (int f = 0; f < 5; f++) {
            for (int k = 0; k < 9; k++) {
                SWORD[f][k] = reg("sword." + Perk.FAMILY_IDS[f] + "." + SWORD_KINDS[k]);
            }
        }

        METEOR_FALLING = reg("meteor_falling");
        METEOR_IMPACT = reg("meteor_impact");
        METEOR_WARNING = reg("meteor_warning");
        METEOR_COUNTDOWN = reg("meteor_countdown");
        METEOR_AMBIENT = reg("meteor_ambient");
    }
    public static Holder<SoundEvent> sword(LivingEntity e, int kind, Holder<SoundEvent> fallback) {
        if (e != null) {
            SetLook look = SetLook.of(e);
            if (look != null) {
                int row = (e instanceof Player p) ? SwordTheme.choiceFor(p, look) : look.ordinal();
                Holder<SoundEvent> h = SWORD[row][kind];
                if (h != null) return h;
                Holder<SoundEvent> orig = SWORD[look.ordinal()][kind];
                if (orig != null) return orig;
            }
        }
        return fallback;
    }
    public static Holder<SoundEvent> equip(int family) { return ARMOR[Math.max(0, family)][2]; }
    public static Holder<SoundEvent> setEquip(SetLook l) { return ARMOR[l.ordinal()][3]; }
    public static Holder<SoundEvent> crystal(int family, int kind) { return CRYSTAL[Math.max(0, Math.min(4, family))][kind]; }
    public static Holder<SoundEvent> wingsToggle(int family, boolean enable) {
        int fam = Math.max(0, Math.min(4, family));
        return WINGS_TOGGLE[fam][enable ? 0 : 1];
    }
    public static Holder<SoundEvent> flightEnter(int family) {
        int fam = Math.max(0, Math.min(4, family));
        Holder<SoundEvent> s = FLIGHT_ENTER[fam];
        if (s == null) s = (fam == 3) ? FLIGHT_ENTER[2] : FLIGHT_ENTER[4];
        return s;
    }
    public static Holder<SoundEvent> flightThrust(int family) {
        int fam = Math.max(0, Math.min(4, family));
        if (fam == 3 && CP_THRUST_LOOP != null) return CP_THRUST_LOOP;   // batch1: Cyber Psycho hum loop
        Holder<SoundEvent> s = FLIGHT_THRUST[fam];
        if (s == null) s = (fam == 3) ? FLIGHT_THRUST[2] : FLIGHT_THRUST[4];
        return s;
    }
    public static Holder<SoundEvent> warpDeparture() { return WARP_DEPARTURE; }
    public static Holder<SoundEvent> warpArrival() { return WARP_ARRIVAL; }
    private ModSounds() {}
}
