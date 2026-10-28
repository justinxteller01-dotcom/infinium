package dev.justin.infinium;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Shared "Hold Left Shift for more" text: tooltips (client) and JEI info pages use the same lines. */
public final class Info {
    private static Component h(String s) { return Component.literal(s).withStyle(ChatFormatting.LIGHT_PURPLE); }
    private static Component l(String s) { return Component.literal(" " + s).withStyle(ChatFormatting.GRAY); }

    /** Perk line: greyed with a dark-grey " Off" suffix when the viewer disabled that perk (13.14 B). */
    private static Component p(String s, Perk perk, long mask) {
        if (perk == null || (mask & perk.bit()) == 0) return l(s);
        return Component.literal(" " + s).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH)
            .append(Component.literal(" Off").withStyle(ChatFormatting.DARK_GRAY));
    }
    public static List<Component> details(ItemStack st) { return details(st, 0L); }

    public static List<Component> details(ItemStack st, long mask) {
        List<Component> o = new ArrayList<>();
        Item it = st.getItem();
        boolean armor = Infinium.isArmor(st);
        boolean tool = Infinium.isTool(st);
        if (it == Infinium.STRANGE_FRAGMENTS) {
            o.add(h("Spacetime Fragments")); o.add(l("Jagged shards of the stone the Infinium helmet's crystal is cut from"));
            o.add(l("Slowly shimmers; sheds faint specks when held or dropped"));
            o.add(h("Uses")); o.add(l("8 around an Amethyst Shard -> Spacetime Crystal"));
            o.add(h("Obtaining")); o.add(l("Structure chests (more in Nether / End / ancient structures, rare in villages)")); return o;
        }
        if (it == Infinium.NETHERITE_ROCKET) {
            o.add(h("Infinium Rocket")); o.add(l("Boosts you while flying, just like a firework rocket. Never used up, no cooldown."));
            o.add(l("Made from any firework rocket + 1 Infinium Ingot in a Smithing Table.")); return o;
        }
        if (it == Infinium.SPACETIME_CRYSTAL) {
            o.add(h("Spacetime Crystal")); o.add(l("A crystal shard brimming with unstable purple energy"));
            o.add(l("Crackles with glitch sparks and spinning specks when held or dropped"));
            o.add(h("Recipe")); o.add(l("8 Spacetime Fragments around an Amethyst Shard"));
            o.add(l("Place in an Infinium armor crystal slot to raise its power and unlock perks."));
            o.add(h("Uses")); o.add(l("Center of the Infinium Ingot recipe.")); o.add(h("Obtaining")); o.add(l("Crafted only")); return o;
        }
        InfiniumStyle ts = StyleTemplates.styleOf(it);
        if (ts != null) {
            o.add(h(switch (ts) { case ASTRAL -> "Astral Upgrade"; case CYBERNETIC -> "Cybernetic Upgrade"; case SINGULARITY -> "Singularity Upgrade"; case CYBERPSYCHO -> "Cyber Psycho Upgrade"; }));
            switch (ts) {
                case ASTRAL -> { o.add(l("Netherite armor -> Astral (template + Infinium Ingot)")); o.add(l("Cybernetic armor -> Astral (template is consumed)")); }
                case CYBERNETIC -> { o.add(l("Netherite armor -> Cybernetic (template + Infinium Ingot)")); o.add(l("Astral armor -> Cybernetic (template is consumed)")); }
                case SINGULARITY -> { o.add(l("ONLY Astral armor (100% charged) -> Singularity (template + Infinium Ingot)")); o.add(l("Cyber Psycho armor -> Singularity (template is consumed)")); }
                case CYBERPSYCHO -> { o.add(l("ONLY Cybernetic armor (100% charged) -> Cyber Psycho (template + Infinium Ingot)")); o.add(l("Singularity armor -> Cyber Psycho (template is consumed)")); }
            }
            o.add(l("Tools have no template: they take the look of your worn full set")); return o;
        }
        if (!armor && !tool) return o;
        String[] FN = Perk.FAMILY_NAMES;
        if (armor) {
            int fam = Perk.family(st);
            int cap = CrystalCharge.slots(st), fil = CrystalCharge.filled(st);
            net.minecraft.world.entity.EquipmentSlot slot = Infinium.isHelmet(st) ? net.minecraft.world.entity.EquipmentSlot.HEAD
                : Infinium.isChestplate(st) ? net.minecraft.world.entity.EquipmentSlot.CHEST : Infinium.isLeggings(st) ? net.minecraft.world.entity.EquipmentSlot.LEGS : net.minecraft.world.entity.EquipmentSlot.FEET;
            if (cap > 0) {
                o.add(Component.literal("Spacetime Energy: ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(fil + " / " + cap + " Crystals (" + Math.round(fil * 100.0 / cap) + "%)").withStyle(ChatFormatting.GRAY)));
            }
            for (String pl : pieceLine(fam, slot).split("\n")) o.add(p(pl, Perk.piece(fam, slot), mask));
            if (slot == net.minecraft.world.entity.EquipmentSlot.CHEST) o.add(l("Wings: Shift + Right Click on air or " + key("toggle_wings", "Toggle Wings", null) + " to toggle."));
            o.add(h("Full set (" + FN[fam] + ")"));
            if (tierNote(fam) != null) o.add(l(tierNote(fam)));
            for (Object[] e : setLines(fam)) o.add(p((String) e[0], (Perk) e[1], mask));
            o.add(Component.literal("Tier: ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(switch (fam) {
                    case 0 -> "Singularity Template + Ingot at 100% charge.";
                    case 1 -> "Cyber Psycho Template + Ingot at 100% charge.";
                    case 4 -> "The top tier. Counts as fully charged lower armor.";
                    default -> "Combine with 100% charged " + (fam == 2 ? "Cyber Psycho" : "Singularity") + " + Ingot.";
                }).withStyle(ChatFormatting.GRAY)));
            o.add(l("Almost broken (" + PerkValues.durabilityFloor() + " durability left): enchantments stop working, perks keep going."));
            return o;
        }
        o.add(h("Tool"));
        if (it == Infinium.SWORD) {
            o.add(l("12 dmg, applies 8s bleed."));
            o.add(l("100% full attack charge: fires 3-block piercing Energy Slash (5.5 dmg, applies bleed)."));
            o.add(l("Swings faster with Cyber armor and hits fast enemies again sooner."));
        }
        if (it == Infinium.PICKAXE) o.add(l("Toggle between Fortune IV or Silk Touch."));
        if (it == Infinium.SHOVEL) o.add(l("1x1, 3x3, and 3x1 digging modes."));
        if (it == Infinium.AXE) o.add(l("Make any mined log drop any log of your choice."));
        if (it == Infinium.HOE) o.add(l("Tills permanently saturated, un-trampleable, 100% lit farmland."));
        if (it == Infinium.PICKAXE || it == Infinium.SHOVEL || it == Infinium.AXE) o.add(l("Sneak + Right Click on air to change modes."));
        o.add(l("Matches the textures & particles of your worn armor (dyed independently)."));
        if (it != Infinium.HOE) o.add(l("Stops working at " + PerkValues.durabilityFloor() + " durability."));
        return o;
    }

    /** Tier 2/3 explainer: the armor already has every lower-tier perk; crystals UPGRADE them. Null for tier 1. */
    public static String tierNote(int fam) {
        return switch (fam) {
            case 2 -> "This armor keeps every perk from Astral. Add crystals to upgrade them to Singularity strength.";
            case 3 -> "This armor keeps every perk from Cybernetic. Add crystals to upgrade them to Cyber Psycho strength.";
            case 4 -> "This armor keeps every perk from the sets below it. Add crystals to upgrade them to Infinity strength.";
            default -> null;
        };
    }

    /** Short form of tierNote for tight spots (matrix sub-header). Null for tier 1. */
    public static String tierNoteShort(int fam) {
        return switch (fam) {
            case 2 -> "Has all Astral perks. Crystals upgrade them.";
            case 3 -> "Has all Cybernetic perks. Crystals upgrade them.";
            case 4 -> "Has all lower perks. Crystals upgrade them.";
            default -> null;
        };
    }

    /** Book-style piece line (also used by the perk settings hover). */
    public static String pieceLine(int fam, net.minecraft.world.entity.EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> switch (fam) {
                // G4b: X-ray is the M3 pulse ability on every helmet (XrayPulse.radius: r10 / r20 / r30), not a continuous X key.
                // Astral (0) split from the shared default so only Astral gets the plain wording.
                case 0 -> "Helmet: Breathe underwater; X-Ray Pulse shows ores within 10 blocks (" + key("xray_pulse", "X-Ray Pulse", null) + ").";
                case 1 -> "Helmet: Breathe underwater forever; " + pulse(10) + ".";
                case 2 -> "Helmet: Breathe underwater forever; " + pulse(20) + ".";
                case 3 -> "Helmet: Breathe underwater forever; " + pulse(20) + ".";
                case 4 -> "Helmet: Breathe underwater forever; " + pulse(30) + ".";
                default -> "Helmet: Infinite oxygen; " + pulse(10) + ".";
            };
            case CHEST -> switch (fam) {
                case 0 -> "Chestplate: Built-in wings you can turn on and off.";
                case 1 -> "Chestplate: Built-in Jet Wings (toggleable).";
                case 2 -> "Chestplate: Built-in Elytra; Flight Thrust (" + thrust() + ").";
                case 3 -> "Chestplate: Built-in Jet Wings; Flight Thrust (" + thrust() + ").";
                case 4 -> "Chestplate: Hybrid Flight & Jet Wings; Flight Thrust (" + thrust() + ").";
                default -> "Chestplate: Built-in Elytra (toggleable).";
            };
            case LEGS -> switch (fam) {
                case 0 -> "Leggings: Move slightly faster; Quantum Tunnel zips you " + (int) SpacetimeWarp.DISTANCE[0] + " blocks forward in mid-air (" + key("warp", "Quantum Tunnel", null) + ").";
                case 1 -> "Leggings: Faster running speed (your view won't zoom), +10%.";
                case 2 -> "Leggings: Move slightly faster, +5%. " + warp((int) SpacetimeWarp.DISTANCE[1]) + ".";
                case 3 -> "Leggings: Super fast running speed (your view won't zoom), +15%.";
                case 4 -> "Leggings: The fastest running speed, +20%. " + warp((int) SpacetimeWarp.DISTANCE[2]) + ".";
                default -> "Leggings: " + pct(PerkValues.legSpeed(PerkValues.Family.values()[fam])) + " Speed Bonus (No FOV change).";
            };
            default -> switch (fam) {
                case 0 -> "Boots: No fall damage.\nHold jump to jump higher (up to 2 blocks).";
                case 1 -> "Boots: No fall damage.";
                case 2 -> "Boots: No fall damage.\nHold jump to jump even higher (up to 3 blocks).";
                case 3 -> "Boots: No fall damage.";
                case 4 -> "Boots: No fall damage.\nHold jump to reach ridiculous heights (up to 4 blocks).";
                default -> "Boots: No fall damage.";
            };
        };
    }
    static String pct(double d) { return Math.round(d * 100) + "%"; }
    /**
     * Bound-key name for runtime text. The client sets this (InfiniumClient) to the KeyMapping's translated key message;
     * ids: flight_thrust, toggle_wings, warp, xray_pulse. Returns null when unknown (server side).
     */
    public static java.util.function.Function<String, String> keyName = id -> null;

    /** "the Flight Thrust key (default Left Ctrl)" style fallback when no client key name is available. */
    public static String key(String id, String what, String def) {
        String n = keyName.apply(id);
        if (n != null) return n;
        return "the " + what + " key (" + (def == null ? "unbound by default" : "default " + def) + ")";
    }
    static String thrust() { return "hold " + key("flight_thrust", "Flight Thrust", "Left Ctrl") + " while flying"; }
    static String pulse(int r) { return "X-Ray Pulse " + r + "m (" + key("xray_pulse", "X-Ray Pulse", null) + ")"; }
    static String warp(int m) { return "Quantum Tunnel " + m + "m (" + key("warp", "Quantum Tunnel", null) + ", mid-air)"; }

    public static String blocks(int n) { return "+" + n + (n == 1 ? " block" : " blocks"); }

    /** Full-set lines {text, perk} for a family, in exact crystal unlock order. */
    public static List<Object[]> setLines(int fam) {
        PerkValues.Family f = PerkValues.Family.values()[fam];
        List<Object[]> o = new ArrayList<>();
        o.add(new Object[]{f == PerkValues.Family.ASTRAL ? "Passive: Takes about a third less damage than netherite; fire and lava can't hurt you." : f == PerkValues.Family.INFINITY ? "Passive: Takes half the damage netherite would; fire, lava and explosions can't hurt you." : "Passive: " + (fam == 1 ? "Takes about a third less damage than netherite" : "Takes almost half the damage netherite would") + " (" + PerkValues.damageTakenText(f) + "); fire and lava can't hurt you.", Perk.set(fam, "immune")});
        for (String kind : CrystalCharge.ORDER[fam]) {
            int req = CrystalCharge.crystalsRequired(fam, kind);
            String prefix = "[" + req + "✦] ";
            String desc = f == PerkValues.Family.ASTRAL ? astralSetLine(kind) : switch (kind) {
                case "regen" -> "Repairs all your gear over time (1 durability every " + (PerkValues.repairTicks(f) / 20) + "s).";
                case "noconsume" -> "Sometimes you get the block back after placing it (" + pct(PerkValues.keepChance(f)) + ").";
                case "reach" -> "Reach " + (int) PerkValues.reach(f) + " blocks farther.";
                case "stacks" -> "Items stack higher (64 to " + PerkValues.boostedMax(f, 64) + ", 16 to " + PerkValues.boostedMax(f, 16) + ", 1 to " + PerkValues.boostedMax(f, 1) + ").";
                case "mining" -> "Mine faster (+" + pct(PerkValues.mining(f)) + ").";
                case "jump" -> "Jump again in mid-air (" + PerkValues.airJumps(f) + " extra). Turn wings off to use it.";
                case "attack" -> "Swing swords and draw bows faster (+" + pct(PerkValues.attackAndBow(f)) + ").";
                case "slow" -> "When you get hit, nearby mobs slow down (" + pct(PerkValues.slowAmount(f)) + " for " + PerkValues.slowDurationTicks(f) / 20 + "s, every " + PerkValues.slowCooldownTicks(f) / 20 + "s).";
                default -> kind;
            };
            o.add(new Object[]{prefix + desc, Perk.set(fam, kind)});
        }
        return o;
    }
    /** Astral-only plain-language set lines (text style: what it does, no percentage dumps). Other families use the shared switch. */
    private static String astralSetLine(String kind) {
        PerkValues.Family f = PerkValues.Family.ASTRAL;
        return switch (kind) {
            case "regen" -> "Slowly repairs all gear in your inventory.";
            case "noconsume" -> "Sometimes doesn't use up the block you place.";
            case "reach" -> blocks((int) PerkValues.reach(f)) + " reach.";
            case "stacks" -> "Items stack higher (64 becomes " + PerkValues.boostedMax(f, 64) + ").";
            default -> kind;
        };
    }
    private Info() {}
}
