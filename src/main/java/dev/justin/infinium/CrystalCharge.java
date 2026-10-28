package dev.justin.infinium;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Crystal fill stored on each armor piece. Filled slots never empty.
 * Astral and Cybernetic: 8 slots; Singularity and Cyber Psycho: 16; Infinity: 32.
 * Milestones are spread evenly: ceil((i+1) * slots / perks) (Astral 2,4,5,7,8; Cybernetic 2,4,6,8;
 * Singularity 4,7,10,13,16; Cyber Psycho 4,8,12,16; Infinity 4,8,11,15,18,22,25,29,32).
 * Sounds: infinium:crystal.<set>.{insert,perk,full,denied}. No removal sound (slots never empty).
 * A set perk turns on only when every worn piece of that set has reached that perk's mark.
 * Fire, lava, explosion, damage taken, and leggings speed are not gated here.
 */
public final class CrystalCharge {
    /** M4: locked slot capacities (config sliders removed) so the milestones in crystalsRequired always match. */
    public static final int T1_SLOTS = 8, T2_SLOTS = 16, T3_SLOTS = 32;
    public static final DataComponentType<Integer> FILLED = DataComponentType.<Integer>builder()
            .persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build();

    /** Crystal unlock order. Index 0 is the first perk. Not the piece defaults.
     *  Locked 2026-10-28: Astral/Singularity repair, block saver, saber, stacks, reach; Cybernetic/Cyber Psycho mining, jump, slow, attack;
     *  Infinity alternates the two: repair, mining, block saver, jump, saber, slow, stacks, attack, reach.
     *  "saber" = the sword's energy slash/beam (no Perk enum entry, not toggleable). Only Astral actually gates it
     *  (tier 2/3 perks are always active; Cybernetic has no saber milestone, so slashes stay free there). */
    public static final String[][] ORDER = {
            {"regen", "noconsume", "saber", "stacks", "reach"},
            {"mining", "jump", "slow", "attack"},
            {"regen", "noconsume", "saber", "stacks", "reach"},
            {"mining", "jump", "slow", "attack"},
            {"regen", "mining", "noconsume", "jump", "saber", "slow", "stacks", "attack", "reach"}
    };

    public record Insert(int containerId, int slot, int targetCrystals) implements CustomPacketPayload {
        public static final Type<Insert> TYPE = new Type<>(Infinium.id("crystal_insert"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Insert> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Insert::containerId,
                ByteBufCodecs.VAR_INT, Insert::slot,
                ByteBufCodecs.VAR_INT, Insert::targetCrystals,
                Insert::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Infinium.id("crystal_filled"), FILLED);
        PayloadTypeRegistry.serverboundPlay().register(Insert.TYPE, Insert.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Insert.TYPE, (payload, ctx) -> insert(ctx.player(), payload));
    }

    public static int slots(ItemStack armor) {
        int fam = Perk.family(armor);
        return switch (fam) {
            case 0, 1 -> T1_SLOTS;
            case 2, 3 -> T2_SLOTS;
            case 4 -> T3_SLOTS;
            default -> 0;
        };
    }

    public static int setSlots(SetLook look) {
        if (look == null) return 0;
        return switch (look) {
            case ASTRAL, CYBERNETIC -> T1_SLOTS;
            case SINGULARITY, CYBERPSYCHO -> T2_SLOTS;
            case INFINITY -> T3_SLOTS;
        };
    }

    public static int setSlots(net.minecraft.world.entity.LivingEntity e) {
        return e == null ? 0 : setSlots(SetLook.of(e));
    }

    public static int filledForFamily(ItemStack s, int targetFam) {
        int pf = Perk.family(s);
        if (pf < 0) return 0;
        int cap = switch (targetFam) {
            case 0, 1 -> T1_SLOTS;
            case 2, 3 -> T2_SLOTS;
            case 4 -> T3_SLOTS;
            default -> 0;
        };
        if (pf > targetFam) return cap;
        return Math.min(cap, filled(s));
    }

    public static int filled(ItemStack armor) {
        Integer n = armor.get(FILLED);
        if (n == null || n < 0) return 0;
        int cap = slots(armor);
        return cap <= 0 ? 0 : Math.min(n, cap);
    }

    public static boolean full(ItemStack armor) {
        int cap = slots(armor);
        return cap > 0 && filled(armor) >= cap;
    }

    /** Full-set crystal count for the active SetLook. Higher-tier pieces worn in a lower-tier set count as 100% full for that lower tier. */
    public static int setFilled( net.minecraft.world.entity.LivingEntity e, SetLook look) {
        if (e == null || look == null) return 0;
        int cap = setSlots(look);
        if (cap <= 0) return 0;
        int min = cap;
        int targetFam = look.ordinal();
        for (EquipmentSlot sl : Infinium.ARMOR_SLOTS) {
            ItemStack s = e.getItemBySlot(sl);
            int pf = Perk.family(s);
            if (pf < 0) return 0;
            if (pf > targetFam) continue; // Higher-tier piece counts as fully upgraded for lower tier
            min = Math.min(min, filled(s));
        }
        return Math.max(0, Math.min(cap, min));
    }

    public static int setFilled(net.minecraft.world.entity.LivingEntity e) {
        return setFilled(e, SetLook.of(e));
    }

    /**
     * Total Spacetime Crystals inserted across the progression continuum (out of 80).
     * Follows count-as progression:
     * - Astral / Cybernetic (0..8)
     * - Singularity / CyberPsycho (8..24) [inherits 8 baseline]
     * - Infinity (48..80) [inherits 24 + 24 = 48 baseline]
     * No full set worn = 0 (M4).
     */
    public static int totalLifetimeCrystals(net.minecraft.world.entity.LivingEntity e) {
        if (e == null) return 0;
        int t1 = T1_SLOTS;
        int t2 = T2_SLOTS;
        int t3 = T3_SLOTS;
        int baseInf = (t1 + t2) * 2;
        SetLook look = SetLook.of(e);
        if (look != null) {
            int filled = setFilled(e, look);
            return switch (look) {
                case ASTRAL, CYBERNETIC -> Math.min(t1, filled);
                case SINGULARITY, CYBERPSYCHO -> t1 + Math.min(t2, filled);
                case INFINITY -> baseInf + Math.min(t3, filled);
            };
        }
        // M4: no solo-chestplate fallback; crystals only count on a full set.
        return 0;
    }

    /** True when the given perk kind has reached its crystal milestone on the current full set. */
    public static boolean setPerkUnlocked(net.minecraft.world.entity.LivingEntity e, SetLook look, String kind) {
        if (e == null || look == null) return false;
        int fam = look.ordinal();
        int idx = orderIndex(fam, kind);
        if (idx < 0) return true;
        int count = ORDER[fam].length;
        int cap = setSlots(look);
        int req = (int) Math.ceil((idx + 1) * (cap / (double) count));
        return setFilled(e, look) >= req;
    }

    /** Crystals required for a specific perk kind on a family (1-based crystal count). */
    public static int crystalsRequired(int family, String kind) {
        int idx = orderIndex(family, kind);
        return crystalsRequired(family, idx);
    }

    public static int crystalsRequired(int family, int idx) {
        if (idx < 0 || family < 0 || family >= ORDER.length) return 0;
        int cap = switch (family) { case 0, 1 -> T1_SLOTS; case 2, 3 -> T2_SLOTS; case 4 -> T3_SLOTS; default -> 0; };
        int count = ORDER[family].length;
        return (int) Math.ceil((idx + 1) * (cap / (double) count));
    }

    /**
     * Linear visual progression across the entire armor spectrum [0.0 .. 1.0]:
     * - Un-upgraded Tier 1 (or individual Tier 1 pieces): 0.00
     * - Tier 1 (Astral / Cybernetic) 0..8 crystals: 0.00 -> 0.33
     * - Tier 2 (Singularity / Cyber Psycho) 0..16 crystals: 0.33 -> 0.67
     * - Tier 3 (Infinity) 0..32 crystals: 0.67 -> 1.00
     */
    public static float visualProgress(net.minecraft.world.entity.LivingEntity e) {
        if (e == null) return 0F;
        SetLook look = SetLook.of(e);
        if (look != null) {
            int cap = setSlots(look);
            int fil = setFilled(e, look);
            float frac = cap > 0 ? Math.min(1F, Math.max(0F, (float) fil / cap)) : 0F;
            return switch (look) {
                case ASTRAL, CYBERNETIC -> 0.33F * frac;
                case SINGULARITY, CYBERPSYCHO -> 0.33F + 0.34F * frac;
                case INFINITY -> 0.67F + 0.33F * frac;
            };
        }
        float best = 0F;
        for (EquipmentSlot sl : Infinium.ARMOR_SLOTS) {
            ItemStack s = e.getItemBySlot(sl);
            if (Infinium.isArmor(s)) best = Math.max(best, pieceVisualProgress(s));
        }
        return best;
    }

    public static float pieceVisualProgress(ItemStack s) {
        int fam = Perk.family(s);
        if (fam < 0) return 0F;
        // M4: include the piece's own fill so a single piece's glow matches the set curve.
        int cap = slots(s);
        float frac = cap > 0 ? Math.min(1F, filled(s) / (float) cap) : 0F;
        return switch (fam) {
            case 0, 1 -> 0.33F * frac;
            case 2, 3 -> 0.33F + 0.34F * frac;
            case 4 -> 0.67F + 0.33F * frac;
            default -> 0F;
        };
    }

    public static boolean gated(String kind) {
        for (String[] row : ORDER) for (String k : row) if (k.equals(kind)) return true;
        return false;
    }

    public static int orderIndex(int family, String kind) {
        if (family < 0 || family >= ORDER.length) return -1;
        String[] row = ORDER[family];
        for (int i = 0; i < row.length; i++) if (row[i].equals(kind)) return i;
        return -1;
    }

    /** Crystals required on this piece before set-perk {@code index} (0-based) of a set with {@code perkCount} perks. */
    public static int need(ItemStack armor, int index, int perkCount) {
        int cap = slots(armor);
        if (cap <= 0 || perkCount <= 0) return Integer.MAX_VALUE;
        return (int) Math.ceil((index + 1) * (cap / (double) perkCount));
    }

    public static boolean pieceReady(ItemStack armor, int index, int perkCount) {
        return slots(armor) > 0 && filled(armor) >= need(armor, index, perkCount);
    }

    /** True when this set perk is active (Tier 1 requires crystal milestone; Tier 2 and Infinity have previous-tier baseline active at 0 crystals). */
    /** Saber milestone (energy slash/beam): gated on Astral only; every other set/tier has it from the start. */
    public static boolean saberUnlocked(net.minecraft.world.entity.LivingEntity e) {
        SetLook look = SetLook.of(e);
        if (look != SetLook.ASTRAL) return true;
        return setPerkUnlocked(e, look, "saber");
    }

    public static boolean unlocked(Player player, Perk perk) {
        if (perk == null || !perk.isSet()) return true;
        int idx = orderIndex(perk.family, perk.kind);
        if (idx < 0) return true;
        SetLook look = SetLook.of(player);
        if (look == null || look.ordinal() != perk.family) return false;
        if (perk.family >= 2) return true; // Tier 2 & Infinity always have baseline perks active; crystals upgrade their values
        return setPerkUnlocked(player, look, perk.kind);
    }

    public static void onSmith(ItemStack out, SmithingRecipeInput input, CallbackInfoReturnable<ItemStack> cir) {
        apply(out, input.base(), input.template(), cir, false);
    }

    /** Infinity combine: both armor pieces must already be at 100%. Charge resets. */
    public static void onTierUp(ItemStack out, SmithingRecipeInput input, CallbackInfoReturnable<ItemStack> cir) {
        apply(out, input.base(), input.template(), cir, true);
    }

    private static void apply(ItemStack out, ItemStack base, ItemStack other, CallbackInfoReturnable<ItemStack> cir, boolean intoInfinity) {
        if (out.isEmpty() || cir.getReturnValue().isEmpty()) return;
        int bf = Perk.family(base);
        int of = Perk.family(out);
        if (bf < 0) {
            out.remove(FILLED);
            return;
        }
        if (!intoInfinity && bf == of) return;
        if (!intoInfinity && swap(bf, of)) {
            int n = filled(base);
            if (n > 0) out.set(FILLED, n); else out.remove(FILLED);
            return;
        }
        if (!full(base) || (Infinium.isArmor(other) && !full(other))) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        out.remove(FILLED);
    }

    private static boolean swap(int a, int b) {
        return (a == 0 && b == 1) || (a == 1 && b == 0) || (a == 2 && b == 3) || (a == 3 && b == 2);
    }

    public static int countAvailableCrystals(Player player, AbstractContainerMenu menu) {
        int total = 0;
        if (menu != null) {
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty() && carried.is(Infinium.SPACETIME_CRYSTAL)) total += carried.getCount();
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(Infinium.SPACETIME_CRYSTAL)) total += s.getCount();
        }
        return total;
    }

    public static boolean consumeCrystals(Player player, AbstractContainerMenu menu, int count) {
        if (count <= 0) return true;
        if (countAvailableCrystals(player, menu) < count) return false;

        int remaining = count;
        if (menu != null) {
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty() && carried.is(Infinium.SPACETIME_CRYSTAL)) {
                int take = Math.min(remaining, carried.getCount());
                carried.shrink(take);
                remaining -= take;
            }
        }
        if (remaining > 0) {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && s.is(Infinium.SPACETIME_CRYSTAL)) {
                    int take = Math.min(remaining, s.getCount());
                    s.shrink(take);
                    remaining -= take;
                }
            }
        }
        return remaining == 0;
    }

    /**
     * One crystal per click (batch1 insert redesign). {@code payload.targetCrystals()} is the set level the client
     * expects AFTER this crystal (= current + 1); a stale/duplicate click (target != current + 1) is ignored silently,
     * so a double click can never skip ahead or spend two. Perks unlock/upgrade when the total crosses their mark
     * (same marks as before: crystalsRequired), so saved FILLED counts keep their meaning.
     */
    public static void insert(ServerPlayer player, Insert payload) {
        AbstractContainerMenu menu = player.containerMenu;
        SetLook look = SetLook.of(player);
        if (look == null) { deny(player, 0); return; }   // M4: full matching set only
        int fam = look.ordinal();
        int cap = setSlots(look);
        int before = setFilled(player, look);   // set level = lowest piece
        if (before >= cap) { deny(player, fam); return; }
        if (payload.targetCrystals() != before + 1) return;   // stale or duplicate click: ignore quietly
        if (!consumeCrystals(player, menu, 1)) { deny(player, fam); return; }

        int after = before + 1;
        // M4: one insert goes into all 4 pieces; each piece is only ever raised. A fresh piece swapped in lowers the
        // set level (min), so it is paid up one crystal at a time like the rest. Higher-tier pieces are left untouched.
        for (EquipmentSlot sl : Infinium.ARMOR_SLOTS) {
            ItemStack piece = player.getItemBySlot(sl);
            if (Perk.family(piece) == fam && filled(piece) < after) piece.set(FILLED, after);
        }

        int perks = ORDER[fam].length;
        boolean crossed = unlockedCount(after, cap, perks) > unlockedCount(before, cap, perks);
        boolean done = after >= cap;
        float progress = (float) after / Math.max(1, cap);
        double x = player.getX(), y = player.getY(), z = player.getZ();
        // Per-click insert sound (placeholder: existing crystal insert event; audio overhaul later). Pitch climbs with the bar.
        player.level().playSound(null, x, y, z, ModSounds.crystal(fam, ModSounds.INSERT).value(), SoundSource.PLAYERS, 0.85F, 0.9F + 0.3F * progress);
        boolean firstMax = done && InfiniumAdvancements.award(player, switch (look) {
            case ASTRAL -> "astral_max_charge";
            case CYBERNETIC -> "cybernetic_max_charge";
            case SINGULARITY -> "singularity_max_charge";
            case CYBERPSYCHO -> "cyberpsycho_max_charge";
            case INFINITY -> "infinity_max_charge";
        });
        if (firstMax) {
            // Grand cue, once per player per set (gated by the max-charge advancement, so never again on relog/re-max).
            player.level().playSound(null, x, y, z, ModSounds.SET_MAXED[fam].value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            player.level().playSound(null, x, y, z, ModSounds.crystal(fam, ModSounds.FULL).value(), SoundSource.PLAYERS, 0.8F, 1.0F);
        } else if (crossed) {
            player.level().playSound(null, x, y, z, ModSounds.PERK_UNLOCK[fam].value(), SoundSource.PLAYERS, 0.95F, 1.0F);
        }
        SetBonuses.markDirty(player);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (menu != null) menu.broadcastChanges();
    }

    private static int unlockedCount(int filled, int cap, int perks) {
        int n = 0;
        for (int i = 0; i < perks; i++) {
            int need = (int) Math.ceil((i + 1) * (cap / (double) perks));
            if (filled >= need) n++;
        }
        return n;
    }

    private static ItemStack find(Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Infinium.SPACETIME_CRYSTAL)) return s;
        }
        return ItemStack.EMPTY;
    }

    private static void deny(ServerPlayer player, int family) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.crystal(family, ModSounds.DENIED).value(), SoundSource.PLAYERS, 0.6F, 1.0F);
    }

    private CrystalCharge() {}
}
