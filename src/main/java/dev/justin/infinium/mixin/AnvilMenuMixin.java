package dev.justin.infinium.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Anvil rules for ALL items (replaces vanilla createResult; R7 12.11 + R8 13.1):
 *  - book / same-item enchant merge: flat 5 levels, never "Too Expensive", REPAIR_COST ignored (no prior-work growth)
 *  - levels combine like two books (N + N -> N+1, never above the vanilla max level; otherwise the higher stays)
 *  - vanilla exclusivity (protection types, sharpness/smite/bane, silk touch/fortune, ...): a conflicting enchant from the right
 *    item is dropped, the left keeps its own; Infinity + Mending on bows stays allowed (EnchantRules). Creative skips only canEnchant.
 *  - repair with the item's repair material: one material fully repairs, 5 levels; same item: full durability
 *  - rename alone: FREE (0 levels; mayPickup fix below). Rename combined with another operation costs just that operation (5).
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {
    @Shadow private int repairItemCountCost;
    @Shadow private String itemName;
    @Shadow @Final private DataSlot cost;
    @Shadow private boolean onlyRenaming;

    protected AnvilMenuMixin(MenuType<?> t, int id, Inventory inv, ContainerLevelAccess a, ItemCombinerMenuSlotDefinition d) { super(t, id, inv, a, d); }

    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void infinium$createResult(CallbackInfo ci) {
        ci.cancel();
        ItemStack left = inputSlots.getItem(0), right = inputSlots.getItem(1);
        onlyRenaming = false; repairItemCountCost = 0; cost.set(0);
        if (left.isEmpty()) { resultSlots.setItem(0, ItemStack.EMPTY); return; }
        ItemStack out = left.copy();
        int levels = 0;
        boolean changed = false;
        if (!right.isEmpty()) {
            if (left.isDamageableItem() && left.isValidRepairItem(right)) {          // material repair: one item, full repair
                if (!left.isDamaged()) { resultSlots.setItem(0, ItemStack.EMPTY); return; }
                out.setDamageValue(0); repairItemCountCost = 1; levels = 5; changed = true;
            } else {
                boolean book = right.is(Items.ENCHANTED_BOOK);
                if (!book && !right.is(left.getItem())) { resultSlots.setItem(0, ItemStack.EMPTY); return; }
                if (!book && left.isDamageableItem()) out.setDamageValue(0);         // same item: combine durability -> full
                ItemEnchantments add = EnchantmentHelper.getEnchantmentsForCrafting(right);
                ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(out));
                boolean any = false;
                for (var e : add.entrySet()) {
                    Holder<Enchantment> h = e.getKey();
                    int lv = e.getIntValue();
                    if (!(player.hasInfiniteMaterials() || out.is(Items.ENCHANTED_BOOK) || h.value().canEnchant(out))) continue;   // valid for this item type
                    boolean clash = false;
                    for (Holder<Enchantment> x : m.keySet()) if (!x.equals(h) && !dev.justin.infinium.EnchantRules.compatible(x, h)) { clash = true; break; }
                    if (clash) continue;   // vanilla: the right item's conflicting enchant is dropped, the left keeps its own
                    int cur = m.getLevel(h), max = h.value().getMaxLevel();
                    int nl = cur == lv ? Math.max(cur, Math.min(lv + 1, max)) : Math.max(cur, lv);   // cap-safe for legacy over-cap levels
                    m.set(h, nl); any = true;
                }
                if (!any && book) { resultSlots.setItem(0, ItemStack.EMPTY); return; }
                EnchantmentHelper.setEnchantments(out, m.toImmutable());
                levels = 5; changed = true;
            }
        }
        // rename
        if (itemName != null && !StringUtils.isBlank(itemName)) {
            if (!itemName.equals(left.getHoverName().getString())) {
                out.set(DataComponents.CUSTOM_NAME, Component.literal(itemName));
                if (!changed) { levels = 0; onlyRenaming = true; }
                changed = true;
            }
        } else if (left.has(DataComponents.CUSTOM_NAME)) {
            out.remove(DataComponents.CUSTOM_NAME);
            if (!changed) { levels = 0; onlyRenaming = true; }
            changed = true;
        }
        if (!changed) { resultSlots.setItem(0, ItemStack.EMPTY); cost.set(0); return; }
        cost.set(levels);
        resultSlots.setItem(0, out);
        broadcastChanges();
    }

    /** Vanilla mayPickup requires cost > 0; a free rename must still be takeable. */
    @Inject(method = "mayPickup(Lnet/minecraft/world/entity/player/Player;Z)Z", at = @At("RETURN"), cancellable = true)
    private void infinium$freeRename(Player p, boolean hasItem, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && cost.get() == 0 && onlyRenaming && !resultSlots.getItem(0).isEmpty()) cir.setReturnValue(true);
    }
}
