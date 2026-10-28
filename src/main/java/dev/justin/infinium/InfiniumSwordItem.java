package dev.justin.infinium;

import com.mojang.serialization.Codec;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Infinium Sword: applies Bleeding on hit, and supports sneak + right-click energy mode toggle.
 * Mode is stored on ItemStack data component (default energy mode = true).
 */
public class InfiniumSwordItem extends Item {
    public static DataComponentType<Boolean> SWORD_ENERGY_MODE;

    public InfiniumSwordItem(Properties p) {
        super(p);
    }

    public static void registerComponent() {
        SWORD_ENERGY_MODE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Infinium.id("sword_energy_mode"),
            DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
    }

    public static boolean isEnergyMode(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        Boolean b = stack.get(SWORD_ENERGY_MODE);
        return b == null ? true : b; // default energy mode
    }

    private InteractionResult toggleMode(Level level, Player player, ItemStack stack) {
        if (InfiniumDurability.disabled(stack)) return InteractionResult.PASS;
        boolean next = !isEnergyMode(stack);
        stack.set(SWORD_ENERGY_MODE, next);
        if (!level.isClientSide()) {
            int tier = InfiniumArmorTier.getTier(player);
            if (next) {
                if (tier > 0) {
                    player.sendOverlayMessage(Component.literal("Mode: Energy").withStyle(ChatFormatting.LIGHT_PURPLE));
                } else {
                    player.sendOverlayMessage(Component.literal("Requires a full Infinium armor set").withStyle(ChatFormatting.DARK_PURPLE));
                }
            } else {
                player.sendOverlayMessage(Component.literal("Mode: Normal").withStyle(ChatFormatting.GRAY));
            }
            Holder<SoundEvent> s = ModSounds.sword(player, ModSounds.MODE_TOGGLE, ModSounds.TOOL_MODE);
            if (s != null) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    s.value(), SoundSource.PLAYERS, 0.65F, next ? 1.15F : 0.90F);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        return super.useOn(ctx);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && ModeFx.aimingAtAir(level, player) && ModeFx.holdingShield(player)) return InteractionResult.PASS; // no mode change while holding a shield
        if (player.isShiftKeyDown() && ModeFx.aimingAtAir(level, player)) {
            return toggleMode(level, player, stack);
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> b, TooltipFlag flag) {
        boolean energy = isEnergyMode(stack);
        b.accept(Component.literal("Mode: " + (energy ? "Energy" : "Normal")).withStyle(energy ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.hurtEnemy(stack, target, attacker);   // batch1: plain melee hits no longer bleed; only slash/beam apply the DoT
    }
}
