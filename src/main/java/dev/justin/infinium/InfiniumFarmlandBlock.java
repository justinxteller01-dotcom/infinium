package dev.justin.infinium;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Never trampled, permanently fully hydrated. Crops on it get max light/growth via CropBlockMixin. */
public class InfiniumFarmlandBlock extends FarmlandBlock {
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static final MapCodec CODEC = Block.simpleCodec(InfiniumFarmlandBlock::new);
    public InfiniumFarmlandBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(MOISTURE, 7));
    }
    @SuppressWarnings("unchecked") @Override public MapCodec<FarmlandBlock> codec() { return CODEC; }
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(MOISTURE) < 7) level.setBlock(pos, state.setValue(MOISTURE, 7), 2);
    }
    @Override public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
        entity.causeFallDamage(fallDistance, 1.0F, entity.damageSources().fall()); // no trampling
    }
}
