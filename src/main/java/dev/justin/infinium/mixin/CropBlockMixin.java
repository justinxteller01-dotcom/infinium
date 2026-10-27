package dev.justin.infinium.mixin;

import dev.justin.infinium.Infinium;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CropBlock.class)
public abstract class CropBlockMixin {
    private static boolean infinium$on(BlockGetter level, BlockPos pos) {
        return Infinium.INFINIUM_FARMLAND != null && level.getBlockState(pos.below()).is(Infinium.INFINIUM_FARMLAND);
    }

    // Max growth speed: vanilla max is 1 + 3 (own moist farmland) + 8 * 3/4 (all neighbours moist) = 10, no row penalty
    @Inject(method = "getGrowthSpeed", at = @At("HEAD"), cancellable = true)
    private static void infinium$speed(Block type, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (infinium$on(level, pos)) cir.setReturnValue(10.0F);
    }

    @Inject(method = "hasSufficientLight", at = @At("HEAD"), cancellable = true)
    private static void infinium$light(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (infinium$on(level, pos)) cir.setReturnValue(true);
    }

    @Redirect(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getRawBrightness(Lnet/minecraft/core/BlockPos;I)I"))
    private int infinium$tickLight(ServerLevel level, BlockPos pos, int darken) {
        return infinium$on(level, pos) ? 15 : level.getRawBrightness(pos, darken);
    }
}
