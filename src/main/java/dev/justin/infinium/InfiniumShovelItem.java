package dev.justin.infinium;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Sneak + use cycles dig mode: 0 = 1x1, 1 = 3x3 plane perpendicular to hit face, 2 = 3 wide (left/right of player facing). */
public class InfiniumShovelItem extends ShovelItem {
    public static DataComponentType<Integer> DIG_MODE;
    static final String[] NAMES = {"1x1", "3x3", "3x1 (wide)"};
    private static final ThreadLocal<Boolean> BREAKING = ThreadLocal.withInitial(() -> false);

    public InfiniumShovelItem(ToolMaterial m, float d, float s, Properties p) { super(m, d, s, p); }

    public static void registerComponent() {
        DIG_MODE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Infinium.id("dig_mode"),
            DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 2)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    }

    public static int mode(ItemStack s) { Integer m = s.get(DIG_MODE); return m == null ? 0 : m; }

    private InteractionResult cycle(Level level, Player player, ItemStack stack) {
        if (!level.isClientSide()) {
            int m = (mode(stack) + 1) % 3;
            stack.set(DIG_MODE, m);
            ModeFx.burst(player, ModeFx.SHOVEL, m, stack);
            player.sendOverlayMessage(Component.literal("Mode: " + NAMES[m]).withStyle(ChatFormatting.YELLOW));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.TOOL_MODE.value(), SoundSource.PLAYERS, 0.65F, 0.95F + 0.10F * m);
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

    /** Called from PlayerBlockBreakEvents.BEFORE (server). Returns extra positions to break after the target. */
    public static List<BlockPos> extraTargets(Level level, Player player, BlockPos pos, BlockState target) {
        List<BlockPos> out = new ArrayList<>();
        if (BREAKING.get()) return out;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof InfiniumShovelItem) || InfiniumDurability.disabled(stack)) return out;
        int m = mode(stack);
        if (m == 0 || !target.is(BlockTags.MINEABLE_WITH_SHOVEL)) return out;
        if (m == 1) {
            HitResult hr = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
            Direction face = hr instanceof BlockHitResult bhr && bhr.getBlockPos().equals(pos) ? bhr.getDirection()
                : Direction.orderedByNearest(player)[0].getOpposite();
            Direction.Axis ax = face.getAxis();
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                out.add(switch (ax) {
                    case X -> pos.offset(0, a, b);
                    case Y -> pos.offset(a, 0, b);
                    case Z -> pos.offset(a, b, 0);
                });
            }
        } else {
            Direction right = player.getDirection().getClockWise();
            out.add(pos.relative(right));
            out.add(pos.relative(right.getOpposite()));
        }
        return out;
    }

    public static void breakExtras(ServerLevel level, ServerPlayer player, List<BlockPos> extras, float targetHardness) {
        if (extras.isEmpty()) return;
        BREAKING.set(true);
        try {
            for (BlockPos p : extras) {
                ItemStack stack = player.getMainHandItem();
                if (!(stack.getItem() instanceof InfiniumShovelItem)) break;
                BlockState s = level.getBlockState(p);
                if (s.isAir() || !s.is(BlockTags.MINEABLE_WITH_SHOVEL)) continue;
                float h = s.getDestroySpeed(level, p);
                if (h < 0 || h > targetHardness) continue;                       // unbreakable / harder than target
                if (!level.mayInteract(player, p) || player.blockActionRestricted(level, p, player.gameMode())) continue; // spawn protection / adventure
                player.gameMode.destroyBlock(p); // fires break events (claims), drops normally, damages tool (unless hoe-style unbreakable)
            }
        } finally { BREAKING.set(false); }
    }
}
