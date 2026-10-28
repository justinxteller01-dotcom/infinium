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
            o.add(p(pieceLine(fam, slot), Perk.piece(fam, slot), mask));
            if (slot == net.minecraft.world.entity.EquipmentSlot.CHEST) o.add(l("Wings: Shift + Right Click on air or " + key("toggle_wings", "Toggle Wings", null) + " to toggle."));
            o.add(h("Full set (" + FN[fam] + ")"));
            if (tierNote(fam) != null) o.add(l(tierNote(fam)));
            for (Object[] e : setLines(fam)) o.add(p((String) e[0], (Perk) e[1], mask));
            o.add(Component.literal("Tier: ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(switch (fam) {
                    case 0 -> "Singularity Template + Ingot at 100% charge.";
                    case 1 -> "Cyber Psycho Template + Ingot at 100% charge.";
                    case 4 -> "Ultimate tier. Counts as maxed lower set piece.";
                    default -> "Combine with 100% charged " + (fam == 2 ? "Cyber Psycho" : "Singularity") + " + Ingot.";
                }).withStyle(ChatFormatting.GRAY)));
            o.add(l("At " + PerkValues.durabilityFloor() + " durability: enchants disable, perks stay."));
            return o;
        }
        o.add(h("Tool"));
        if (it == Infinium.SWORD) {
            o.add(l("12 dmg, applies 8s bleed."));
            o.add(l("100% full attack charge: fires 3-block piercing Energy Slash (5.5 dmg, applies bleed)."));
            o.add(l("Scales with Cyber attack speed boosts & compresses mob damage i-frames."));
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
                case 0 -> "Helmet: Breathe underwater; Full Bright lets you see in the dark (" + fullBright() + "); X-Ray Pulse shows ores within 10 blocks (" + key("xray_pulse", "X-Ray Pulse", null) + ").";
                case 1 -> "Helmet: Infinite oxygen; " + pulse(10) + ".";
                case 2 -> "Helmet: Infinite oxygen; Full Bright toggle (" + fullBright() + "); " + pulse(20) + ".";
                case 3 -> "Helmet: Infinite oxygen; " + pulse(20) + ".";
                case 4 -> "Helmet: Infinite oxygen; Full Bright toggle (" + fullBright() + "); " + pulse(30) + ".";
                default -> "Helmet: Infinite oxygen; Full Bright toggle (" + fullBright() + "); " + pulse(10) + ".";
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
                case 0 -> "Leggings: Run a little faster; Quantum Tunnel zips you " + (int) SpacetimeWarp.DISTANCE[0] + " blocks forward in mid-air (" + key("warp", "Quantum Tunnel", null) + ").";
                case 1 -> "Leggings: +10% Speed Bonus (No FOV change).";
                case 2 -> "Leggings: +5% Speed Bonus. " + warp((int) SpacetimeWarp.DISTANCE[1]) + ".";
                case 3 -> "Leggings: +15% Speed Bonus (No FOV change).";
                case 4 -> "Leggings: +20% Speed Bonus. " + warp((int) SpacetimeWarp.DISTANCE[2]) + ".";
                default -> "Leggings: " + pct(PerkValues.legSpeed(PerkValues.Family.values()[fam])) + " Speed Bonus (No FOV change).";
            };
            default -> switch (fam) {
                case 0 -> "Boots: No fall damage; +1 jump height.";
                case 1 -> "Boots: No fall damage.";
                case 2 -> "Boots: No fall damage; Jump Augmentation (+2 blocks height, hold jump).";
                case 3 -> "Boots: No fall damage.";
                case 4 -> "Boots: No fall damage; Jump Augmentation (+3 blocks height, hold jump).";
                default -> "Boots: No fall damage.";
            };
        };
    }
    static String pct(double d) { return Math.round(d * 100) + "%"; }
    /**
     * Bound-key name for runtime text. The client sets this (InfiniumClient) to the KeyMapping's translated key message;
     * ids: flight_thrust, toggle_fullbright, toggle_wings, warp, xray_pulse. Returns null when unknown (server side).
     */
    public static java.util.function.Function<String, String> keyName = id -> null;

    /** "the Flight Thrust key (default Left Ctrl)" style fallback when no client key name is available. */
    public static String key(String id, String what, String def) {
        String n = keyName.apply(id);
        if (n != null) return n;
        return "the " + what + " key (" + (def == null ? "unbound by default" : "default " + def) + ")";
    }
    static String thrust() { return "hold " + key("flight_thrust", "Flight Thrust", "Left Ctrl") + " while flying"; }
    static String fullBright() { return key("toggle_fullbright", "Full Bright", "B") + " / button"; }
    static String pulse(int r) { return "X-Ray Pulse " + r + "m (" + key("xray_pulse", "X-Ray Pulse", null) + ")"; }
    static String warp(int m) { return "Quantum Tunnel " + m + "m (" + key("warp", "Quantum Tunnel", null) + ", mid-air)"; }

    public static String blocks(int n) { return "+" + n + (n == 1 ? " block" : " blocks"); }

    /** Full-set lines {text, perk} for a family, in exact crystal unlock order. */
    public static List<Object[]> setLines(int fam) {
        PerkValues.Family f = PerkValues.Family.values()[fam];
        List<Object[]> o = new ArrayList<>();
        o.add(new Object[]{f == PerkValues.Family.ASTRAL ? "Passive: Tougher than netherite; fire and lava can't hurt you." : f == PerkValues.Family.INFINITY ? "Passive: -50% netherite dmg; Fire, lava & explosion immune; Rocket-free flight." : "Passive: " + PerkValues.damageTakenText(f) + " dmg vs netherite; Fire & lava immune.", Perk.set(fam, "immune")});
        for (String kind : CrystalCharge.ORDER[fam]) {
            int req = CrystalCharge.crystalsRequired(fam, kind);
            String prefix = "[" + req + "✦] ";
            String desc = f == PerkValues.Family.ASTRAL ? astralSetLine(kind) : switch (kind) {
                case "regen" -> "Auto-repair: 1 dur/" + (PerkValues.repairTicks(f) / 20) + "s to all inventory gear.";
                case "noconsume" -> pct(PerkValues.keepChance(f)) + " chance to not consume building blocks.";
                case "reach" -> blocks((int) PerkValues.reach(f)) + " reach (place, break, attack).";
                case "stacks" -> "Stacks: 64->" + PerkValues.boostedMax(f, 64) + ", 16->" + PerkValues.boostedMax(f, 16) + ", 1->" + PerkValues.boostedMax(f, 1) + ".";
                case "mining" -> "+" + pct(PerkValues.mining(f)) + " mining speed.";
                case "jump" -> PerkValues.airJumpsText(f) + " (press jump mid-air).";
                case "attack" -> "+" + pct(PerkValues.attackAndBow(f)) + " attack & bow draw speed.";
                case "slow" -> "Nearby mobs (16m) slowed " + pct(PerkValues.slowAmount(f)) + " on hit for " + PerkValues.slowDurationTicks(f) / 20 + "s (" + PerkValues.slowCooldownTicks(f) / 20 + "s cd).";
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
