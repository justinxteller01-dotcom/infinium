package dev.justin.infinium;

import com.mojang.serialization.Codec;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/** Built-in loot mode (sneak + use to toggle): 0 = Fortune IV, 1 = Silk Touch. Applied only to the loot-context tool copy. */
public class InfiniumPickaxeItem extends Item {
    public static DataComponentType<Integer> LOOT_MODE;
    static final String[] NAMES = {"Fortune IV", "Silk Touch"};
    public InfiniumPickaxeItem(Properties p) { super(p); }

    public static void registerComponent() {
        LOOT_MODE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Infinium.id("loot_mode"),
            DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 1)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    }
    public static int mode(ItemStack s) { Integer m = s.get(LOOT_MODE); return m == null ? 0 : m; }

    private InteractionResult cycle(Level level, Player player, ItemStack stack) {
        if (!level.isClientSide()) {
            int m = 1 - mode(stack);
            stack.set(LOOT_MODE, m);
            ModeFx.burst(player, ModeFx.PICKAXE, m, stack);
            player.sendOverlayMessage(Component.literal("Mode: " + NAMES[m]).withStyle(ChatFormatting.LIGHT_PURPLE));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.TOOL_MODE.value(), SoundSource.PLAYERS, 0.65F, m == 0 ? 0.95F : 1.10F);
        }
        return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult useOn(UseOnContext ctx) {
        if (InfiniumDurability.disabled(ctx.getItemInHand())) return InteractionResult.PASS;   // R4: a tool at the floor acts like an empty hand
        Player p = ctx.getPlayer();
        return super.useOn(ctx);
    }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (InfiniumDurability.disabled(player.getItemInHand(hand))) return InteractionResult.PASS;   // R4: a tool at the floor acts like an empty hand
        if (player.isShiftKeyDown() && ModeFx.aimingAtAir(level, player) && ModeFx.holdingShield(player)) return InteractionResult.PASS; // no mode change while holding a shield
        if (player.isShiftKeyDown() && ModeFx.aimingAtAir(level, player)) return cycle(level, player, player.getItemInHand(hand)); // air only
        return super.use(level, player, hand);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> b, TooltipFlag flag) {
        b.accept(Component.literal("Mode: " + NAMES[mode(stack)]).withStyle(ChatFormatting.DARK_PURPLE));
    }

    /** Returns a loot-only copy of the tool with the built-in mode applied (takes priority over real enchants). */
    public static ItemStack lootTool(ServerLevel level, ItemStack tool) {
        var reg = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> fortune = reg.getOrThrow(Enchantments.FORTUNE), silk = reg.getOrThrow(Enchantments.SILK_TOUCH);
        ItemStack copy = tool.copy();
        int m = mode(tool);
        int realFortune = EnchantmentHelper.getItemEnchantmentLevel(fortune, tool);
        EnchantmentHelper.updateEnchantments(copy, e -> {
            if (m == 0) { e.removeIf(h -> h.equals(silk)); e.set(fortune, Math.max(4, realFortune)); }
            else { e.removeIf(h -> h.equals(fortune)); e.set(silk, 1); }
        });
        return copy;
    }
}
