package dev.justin.infinium;

import com.mojang.serialization.Codec;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Sneak + Right-Click on air toggles till mode: 0 = one block, 1 = 3x3 centered on the clicked block. Always Infinium farmland. */
public class InfiniumHoeItem extends HoeItem {
    public static DataComponentType<Integer> HOE_MODE;
    static final String[] NAMES = {"Standard", "3x3"};

    public InfiniumHoeItem(ToolMaterial m, float dmg, float spd, Properties p) { super(m, dmg, spd, p); }

    public static void registerComponent() {
        HOE_MODE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Infinium.id("hoe_mode"),
            DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 1)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    }

    public static int mode(ItemStack s) { Integer m = s.get(HOE_MODE); return m == null ? 0 : m; }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (InfiniumDurability.disabled(stack)) return InteractionResult.PASS;   // R4
        if (player.isShiftKeyDown() && ModeFx.aimingAtAir(level, player) && ModeFx.holdingShield(player)) return InteractionResult.PASS;
        if (player.isShiftKeyDown() && ModeFx.aimingAtAir(level, player)) {
            if (!level.isClientSide()) {
                int m = (mode(stack) + 1) % 2;
                stack.set(HOE_MODE, m);
                ModeFx.burst(player, ModeFx.SHOVEL, m, stack);   // 1x1 / 3x3 pattern
                player.sendOverlayMessage(Component.literal("Mode: " + NAMES[m]).withStyle(ChatFormatting.LIGHT_PURPLE));
                ModSounds.modeSwitch(player, m);
            }
            return InteractionResult.SUCCESS;
        }
        return super.use(level, player, hand);
    }

    @Override public InteractionResult useOn(UseOnContext ctx) {
        ItemStack stack = ctx.getItemInHand();
        if (InfiniumDurability.disabled(stack)) return InteractionResult.PASS;   // R4: a tool at the floor acts like an empty hand
        InteractionResult r = tillOne(ctx, ctx.getClickedPos());
        if (mode(stack) == 1 && ctx.getPlayer() != null) {
            BlockPos c = ctx.getClickedPos();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (stack.isEmpty() || InfiniumDurability.disabled(stack)) break;
                BlockPos p = c.offset(dx, 0, dz);
                Player pl = ctx.getPlayer();
                if (!ctx.getLevel().mayInteract(pl, p) || !pl.mayUseItemAt(p, ctx.getClickedFace(), stack)) continue;
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(p), ctx.getClickedFace(), p, false);
                InteractionResult rr = tillOne(new UseOnContext(ctx.getLevel(), pl, ctx.getHand(), stack, hit) {}, p);
                if (rr.consumesAction() && !r.consumesAction()) r = rr;
            }
        }
        return r;
    }

    /** Vanilla till rules (tillable block, air above, durability, sounds), then upgrade to Infinium farmland. */
    private InteractionResult tillOne(UseOnContext ctx, BlockPos pos) {
        Level level = ctx.getLevel();
        boolean wasFarmland = level.getBlockState(pos).is(Blocks.FARMLAND);
        InteractionResult r = super.useOn(ctx);
        if (!level.isClientSide() && level.getBlockState(pos).is(Blocks.FARMLAND)) {
            level.setBlock(pos, Infinium.INFINIUM_FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7), 11);
            if (wasFarmland) return InteractionResult.SUCCESS;
        }
        return r;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> b, TooltipFlag flag) {
        b.accept(Component.literal("Mode: " + NAMES[mode(stack)]).withStyle(ChatFormatting.DARK_PURPLE));
    }
}
