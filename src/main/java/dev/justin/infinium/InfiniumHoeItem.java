package dev.justin.infinium;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;

public class InfiniumHoeItem extends HoeItem {
    public InfiniumHoeItem(ToolMaterial m, float dmg, float spd, Properties p) { super(m, dmg, spd, p); }
    @Override public InteractionResult useOn(UseOnContext ctx) {
        if (InfiniumDurability.disabled(ctx.getItemInHand())) return InteractionResult.PASS;   // R4: a tool at the floor acts like an empty hand
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        boolean wasFarmland = level.getBlockState(pos).is(Blocks.FARMLAND);
        InteractionResult r = super.useOn(ctx);
        if (!level.isClientSide() && (level.getBlockState(pos).is(Blocks.FARMLAND))) {
            level.setBlock(pos, Infinium.INFINIUM_FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7), 11);
            if (wasFarmland) return InteractionResult.SUCCESS;
        }
        return r;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> b, TooltipFlag flag) {
        b.accept(Component.literal("Mode: Infinium Farmland").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
